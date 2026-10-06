package locality.bench.verification

import locality.bench.run.*
import locality.bench.run.JsonSupport.*
import locality.llm.*
import locality.llm.json.*
import java.nio.file.{Files, Path}
import java.time.{Instant, LocalDate, ZoneOffset}

/** Verification-only entry point; loaded by an ephemeral sbt Test source setting. */
object P2CapabilityProbe:
  private val expectedSource = "41ffc2647b8f3c3838a17c2425b0be97007fe611f5d0a68d3dd9d1c79e1b0b96"
  def main(args: Array[String]): Unit =
    var knownSecrets = Vector.empty[String]
    try
      val allowed = Set("run", "out", "execute", "max-http-attempts", "local-token-cap")
      var options = Map.empty[String, String]
      var index = 0
      while index < args.length do
        require(args(index).startsWith("--"), "Expected --option")
        val key = args(index).drop(2)
        require(allowed(key) && !options.contains(key), "Unknown or duplicate option")
        if key == "execute" then options += key -> "true"
        else
          require(index + 1 < args.length && !args(index + 1).startsWith("--"), "Missing option value")
          index += 1
          options += key -> args(index)
        index += 1
      def required(key: String): String = options.getOrElse(key, throw new IllegalArgumentException(s"Missing --$key"))
      val run = Path.of(required("run"))
      val out = Path.of(required("out"))
      require(run.toAbsolutePath.normalize != out.toAbsolutePath.normalize, "Probe output must be separate from experiment run")
      val runStore = new RunStore(run)
      val protocol = try
        Planner.audit(runStore)
        Protocol.from(parse(runStore.read("protocol.json")))
      finally runStore.close()
      require(protocol.phase == "p2" && protocol.preset == "pilot", "Probe requires the saved P2 pilot plan")
      if !options.contains("execute") then
        require(!options.contains("max-http-attempts") && !options.contains("local-token-cap"), "Numerical execution limits require --execute")
        println(s"P2 capability probe plan only: low / 16384; protocol=${protocol.protocolHash}; zero HTTP attempts.")
      else
        val limits = Limits(1, Math.toIntExact(required("max-http-attempts").toLong), required("local-token-cap").toLong)
        validate(protocol, limits)
        val key = sys.env.get("OPENAI_API_KEY").filter(_.nonEmpty).getOrElse(throw new IllegalArgumentException("OPENAI_API_KEY missing"))
        val project = sys.env.get("OPENAI_PROJECT").filter(_.nonEmpty)
        val organization = sys.env.get("OPENAI_ORGANIZATION").filter(_.nonEmpty)
        knownSecrets = Vector(Some(key), project, organization).flatten
        probe(out, protocol, new OpenAiClient(OpenAiConfig(key, project = project, organization = organization)), limits)
    catch
      case e: Exception =>
        val message = knownSecrets.foldLeft(Option(e.getMessage).getOrElse(e.getClass.getSimpleName))((m, secret) => m.replace(secret, "[REDACTED]"))
        System.err.println("ERROR: " + message)
        sys.exit(1)
  private def validate(protocol: Protocol, limits: Limits): Unit =
    require(protocol.phase == "p2" && protocol.preset == "pilot", "Not the P2 pilot protocol")
    require(str(protocol.config, "sourceHash") == expectedSource && Protocol.sourceHash == expectedSource, "P2 source changed")
    require(protocol.model == "gpt-5.6-terra" && str(protocol.config, "reasoningEffort") == "low", "P2 model settings changed")
    require(protocol.integer("maxOutputTokensGeneration") == 16384, "P2 full-program output cap changed")
    require(limits.maxHttpAttempts >= 2, "Probe requires count plus generation allowance")
    require(limits.localTokenCap >= 16640, "Token cap cannot cover minimum 16384+256 reservation")
  def probe(out: Path, protocol: Protocol, client: LlmClient, limits: Limits, synthetic: Boolean = false): Unit =
    validate(protocol, limits)
    val store = new RunStore(out)
    val window = LocalDate.now(ZoneOffset.UTC).toString
    val budget = new Budget(limits.copy(maxGenerationCalls = 1), 0, Vector.empty)
    var initialized = false
    var countedSuccessfully = false
    val request = LlmRequest(protocol.model, "Return only the requested decimal integer.", Vector(TextMessage("user", "Return only 5916.")), Some("low"), 16384)
    def capability(passed: Boolean, error: Option[LlmError] = None): Obj =
      Json.obj("model" -> s(protocol.model), "reasoningEffort" -> s("low"), "maxOutputTokensFullProgram" -> n(16384),
        "maxOutputTokensGeneration" -> n(16384), "endpoint" -> s("https://api.openai.com/v1"),
        "protocolHash" -> s(protocol.protocolHash), "sourceHash" -> s(expectedSource),
        "helperHash" -> s(sha(Files.readString(Path.of("tools/P2CapabilityProbe.scala")))),
        "requestHash" -> s(hash(requestJson(request))), "tokenCountSupported" -> Bool(countedSuccessfully),
        "generationSupported" -> Bool(passed), "verified" -> Bool(passed), "synthetic_mock" -> Bool(synthetic),
        "capabilityScope" -> s("Tiny request verifies model/effort/output-cap and transport schema; not full-program experiment performance"),
        "checkedAt" -> s(Instant.now.toString), "knownTokens" -> n(budget.knownTokens), "unresolvedTokens" -> n(budget.reservedTokens),
        "httpAttempts" -> n(budget.httpAttempts), "logicalCalls" -> n(budget.logicalCalls), "localTokenCap" -> n(limits.localTokenCap),
        "error" -> error.map(Runner.errorJson).getOrElse(Null))
    try
      require(!Vector("request.json", "count-attempt-001-request.json", "capabilities.json", "token-count.json", "events.jsonl", "usage-ledger.jsonl").exists(store.exists),
        "Probe output was initialized; use a fresh directory and retain prior usage/reservations")
      initialized = true
      store.atomic("capabilities.json", Json.canonical(capability(false)))
      store.atomic("request.json", ResponsesRequestJson.generationBody(request))
      require(budget.http(), "HTTP limit before count")
      val countStart = System.currentTimeMillis()
      store.atomic("count-attempt-001-request.json", ResponsesRequestJson.countInputTokensBody(request))
      store.append("events.jsonl", Json.canonical(Json.obj("kind" -> s("token_count_attempt"), "attempt" -> n(1), "time" -> s(Instant.now.toString), "window" -> s(window))))
      val counted = client.countInputTokens(request)
      client match
        case diagnostics: CountDiagnostics => diagnostics.takeCountHttpResult().foreach { http =>
          store.atomic("count-attempt-001-body.txt", http.body)
          store.atomic("count-attempt-001-meta.json", Json.canonical(Json.obj("httpStatus" -> n(http.status), "requestId" -> opt(http.header("x-request-id")), "durationMs" -> n(System.currentTimeMillis() - countStart), "redactionPolicy" -> s("known_secret_values_masked"))))
        }
        case _ => ()
      counted match
        case Left(error) =>
          store.atomic("capabilities.json", Json.canonical(capability(false, Some(error))))
          throw new IllegalStateException("Token count probe failed: " + Runner.errorMessage(error))
        case Right(tokens) =>
          countedSuccessfully = true
          require(tokens >= 0, "Negative token count")
          store.atomic("token-count.json", Json.canonical(Json.obj("input_tokens" -> n(tokens), "durationMs" -> n(System.currentTimeMillis() - countStart))))
          val reservation = Math.addExact(Math.addExact(tokens, 16384L), 256L)
          require(budget.start("p2-preflight/1", reservation), "Token cap insufficient for counted reservation")
          require(budget.http(), "HTTP limit before generation")
          require(LocalDate.now(ZoneOffset.UTC).toString == window, "UTC window changed; do not dispatch")
          store.append("usage-ledger.jsonl", Json.canonical(Json.obj("kind" -> s("reserve"), "reservationId" -> s("p2-preflight/1"), "tokens" -> n(reservation), "window" -> s(window), "time" -> s(Instant.now.toString))))
          store.append("events.jsonl", Json.canonical(Json.obj("kind" -> s("dispatch"), "time" -> s(Instant.now.toString))))
          val generationStart = System.currentTimeMillis()
          client.generate(request) match
            case Left(error) =>
              store.atomic("attempt-001-meta.json", Json.canonical(Runner.errorJson(error)))
              error match
                case e: ApiError => store.atomic("attempt-001-body.txt", e.rawBody)
                case e: DecodeError => store.atomic("attempt-001-body.txt", e.rawBody)
                case _ => ()
              store.atomic("capabilities.json", Json.canonical(capability(false, Some(error))))
              throw new IllegalStateException("Generation probe failed: " + Runner.errorMessage(error))
            case Right(response) =>
              store.atomic("attempt-001-body.txt", response.rawBody)
              store.atomic("attempt-001-meta.json", Json.canonical(Json.obj("responseId" -> s(response.responseId), "requestId" -> opt(response.requestId), "returnedModel" -> s(response.returnedModel), "status" -> s(response.status), "usage" -> Runner.usageJson(response.usage), "durationMs" -> n(System.currentTimeMillis() - generationStart))))
              response.usage.foreach { usage =>
                budget.settle("p2-preflight/1", Some(usage.totalTokens))
                store.append("usage-ledger.jsonl", Json.canonical(Json.obj("kind" -> s("settle"), "reservationId" -> s("p2-preflight/1"), "tokens" -> n(usage.totalTokens), "window" -> s(window))))
              }
              val passed = response.status == "completed" && response.refusals.isEmpty && response.text.trim == "5916" && response.usage.nonEmpty &&
                response.usage.forall(_.inputTokens <= tokens + 256) && budget.knownTokens <= limits.localTokenCap && budget.reservedTokens == 0
              val evidence = Obj(capability(passed).fields ++ Vector("generationResponseId" -> s(response.responseId), "returnedModel" -> s(response.returnedModel)))
              store.atomic("capabilities.json", Json.canonical(evidence))
              require(passed, "Probe incomplete/refusal/wrong text/unknown usage/token drift; no automatic retry")
              println(s"P2 capability probe passed: low / 16384; ${budget.httpAttempts} attempts, ${budget.knownTokens} known, ${budget.reservedTokens} unresolved tokens; synthetic_mock=$synthetic")
    finally
      try
        if initialized then
          store.atomic(s"budget-$window.json", Json.canonical(Json.obj("localTokenCap" -> n(limits.localTokenCap), "knownTokens" -> n(budget.knownTokens), "unresolvedTokens" -> n(budget.reservedTokens), "httpAttempts" -> n(budget.httpAttempts), "logicalCalls" -> n(budget.logicalCalls))))
      finally store.close()
