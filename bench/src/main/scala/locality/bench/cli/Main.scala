package locality.bench.cli

import locality.bench.run.*
import locality.llm.*
import locality.llm.json.*
import locality.bench.run.JsonSupport.*
import java.nio.file.{Files, Path}

object Main:
  private val commands = Set("plan", "audit", "run", "resume", "score", "report", "preflight", "freeze")
  private val flags = Set("help", "execute", "mock", "retry-ambiguous", "soft-budget", "count-tokens", "allow-carried-reservations")
  private val values = Set("preset", "phase", "out", "run", "model", "config", "max-generation-calls", "max-http-attempts", "local-token-cap", "protocol-hash", "capabilities")
  def main(args: Array[String]): Unit =
    try dispatch(args.toVector)
    catch
      case e: Exception =>
        val secrets = Vector("OPENAI_API_KEY", "OPENAI_PROJECT", "OPENAI_ORGANIZATION").flatMap(sys.env.get).filter(_.nonEmpty)
        val message = secrets.foldLeft(Option(e.getMessage).getOrElse(e.getClass.getSimpleName))((m, secret) => m.replace(secret, "[REDACTED]"))
        System.err.println("ERROR: " + message)
        sys.exit(1)
  def dispatch(args: Vector[String]): Unit =
    if args.isEmpty || args == Vector("--help") then
      help()
      return
    require(commands(args.head), "Unknown command")
    val command = args.head
    var options = Map.empty[String, String]
    var index = 1
    while index < args.size do
      require(args(index).startsWith("--"), "Expected --option")
      val key = args(index).drop(2)
      require(!options.contains(key), s"Duplicate option --$key")
      if flags(key) then options += key -> "true"
      else
        require(values(key), s"Unknown option --$key")
        require(index + 1 < args.size && !args(index + 1).startsWith("--"), s"Missing value --$key")
        index += 1
        options += key -> args(index)
      index += 1
    if options.contains("help") then
      help()
      return
    val common = Set("help")
    val allowed = command match
      case "plan" => Set("preset", "phase", "out", "model", "config", "count-tokens", "execute", "max-http-attempts")
      case "preflight" => Set("out", "model", "execute", "max-http-attempts", "local-token-cap")
      case "run" | "resume" => Set("run", "execute", "mock", "model", "max-generation-calls", "max-http-attempts", "local-token-cap", "protocol-hash", "retry-ambiguous", "soft-budget", "allow-carried-reservations")
      case "freeze" => Set("run", "capabilities")
      case _ => Set("run")
    require(options.keySet.subsetOf(allowed ++ common), "Option not applicable to command")
    def required(key: String): String = options.getOrElse(key, throw new IllegalArgumentException(s"Missing --$key"))
    def positive(key: String): Long =
      val value = required(key).toLong
      require(value > 0, s"--$key must be positive")
      value
    def intLimit(key: String): Int = Math.toIntExact(positive(key))
    Vector("max-generation-calls", "max-http-attempts", "local-token-cap").filter(options.contains).foreach(positive)
    def liveClient: LlmClient =
      val key = sys.env.get("OPENAI_API_KEY").filter(_.nonEmpty).getOrElse(throw new IllegalArgumentException("OPENAI_API_KEY missing; dot-source scripts/import-env.ps1"))
      new OpenAiClient(OpenAiConfig(key, project = sys.env.get("OPENAI_PROJECT").filter(_.nonEmpty), organization = sys.env.get("OPENAI_ORGANIZATION").filter(_.nonEmpty)))
    command match
      case "plan" =>
        val protocol = Protocol.load(options.getOrElse("preset", "smoke"), options.getOrElse("phase", "p0"), options.get("config").map(Path.of(_)), options.get("model"))
        if options.contains("count-tokens") then require(options.contains("execute") && options.contains("max-http-attempts"), "Token count is HTTP: require --execute and --max-http-attempts")
        else require(!options.contains("execute") && !options.contains("max-http-attempts"), "--execute/count limit apply only to --count-tokens")
        val out = Path.of(required("out"))
        Planner.save(protocol, out)
        if options.contains("count-tokens") then
          val store = new RunStore(out)
          try
            val client = liveClient
            val throttle = new Throttle(protocol.integer("maxRpm"), protocol.integer("maxTpm"), SystemRunClock)
            var attempts = 0
            store.jsonLines("cases.jsonl").foreach { c =>
              if attempts < intLimit("max-http-attempts") then
                val id = str(c, "id")
                val request = requestFrom(parse(store.read(s"trials/$id/request.json")))
                val requestHash = hash(requestJson(request))
                if !store.exists(s"token-count-cache/$requestHash.json") then
                  attempts += 1
                  throttle.acquire(0)
                  store.atomic(s"trials/$id/plan-token-count-request.json", ResponsesRequestJson.countInputTokensBody(request))
                  store.append("events.jsonl", Json.canonical(Json.obj("kind" -> s("planning_token_count_attempt"), "trialId" -> s(id), "attempt" -> n(1), "time" -> s(java.time.Instant.now.toString))))
                  val counted = client.countInputTokens(request)
                  client match
                    case diagnostics: CountDiagnostics => diagnostics.takeCountHttpResult().foreach { http =>
                      store.atomic(s"trials/$id/plan-token-count-body.txt", http.body)
                      store.atomic(s"trials/$id/plan-token-count-meta.json", Json.canonical(Json.obj("httpStatus" -> n(http.status), "requestId" -> opt(http.header("x-request-id")))))
                    }
                    case _ => ()
                  counted match
                    case Right(tokens) => store.atomic(s"token-count-cache/$requestHash.json", Json.canonical(Json.obj("input_tokens" -> n(tokens), "requestHash" -> s(requestHash))))
                    case Left(error) => throw new IllegalStateException(Runner.errorMessage(error))
            }
            println(s"Token count HTTP attempts=$attempts; generation requests=0")
          finally store.close()
      case "preflight" =>
        if !options.contains("execute") then println("Preflight plan only: 1 logical generation trial, token-count plus generation; zero HTTP attempts.")
        else Preflight.run(Path.of(required("out")), options.get("model").orElse(sys.env.get("OPENAI_MODEL")).getOrElse("gpt-5.6-terra"), liveClient, Limits(1, intLimit("max-http-attempts"), positive("local-token-cap")))
      case other =>
        val store = new RunStore(Path.of(required("run")))
        try
          other match
            case "audit" => Planner.audit(store); println("Audit passed: dataset, paired gold, independent oracles, prompts and schedule.")
            case "freeze" => Planner.audit(store); println("Frozen protocol: " + Freeze.create(store, Path.of(required("capabilities"))))
            case "score" | "report" => Results.write(store)
            case "run" | "resume" =>
              val p = Protocol.from(parse(store.read("protocol.json")))
              options.get("model").foreach(m => require(m == p.model, "Resume model override differs from saved model"))
              require(!(options.contains("mock") && options.contains("execute")), "Use either --mock or --execute")
              if options.contains("execute") && p.preset == "main" then require(options.get("protocol-hash").exists(h => Freeze.verify(store, h)), "Main live requires unchanged freeze and explicit matching --protocol-hash")
              val mock = options.contains("mock")
              val execute = options.contains("execute")
              if !execute && !mock then println(s"Plan only: ${num(parse(store.read("manifest.json")), "planned")} requests; zero HTTP attempts.")
              else
                val limits = if mock then Limits(options.get("max-generation-calls").map(_.toInt).getOrElse(num(parse(store.read("manifest.json")), "planned").toInt), options.get("max-http-attempts").map(_.toInt).getOrElse(Int.MaxValue), options.get("local-token-cap").map(_.toLong).getOrElse(Long.MaxValue))
                  else Limits(intLimit("max-generation-calls"), intLimit("max-http-attempts"), positive("local-token-cap"))
                val client = if mock then new MockClient(store) else liveClient
                Runner.execute(store, client, limits, execute, mock, options.contains("retry-ambiguous"), options.contains("soft-budget"), allowCarriedReservations = options.contains("allow-carried-reservations"), protocolHash = options.get("protocol-hash"))
            case _ => throw new IllegalArgumentException("Unknown command")
        finally store.close()
  private def help(): Unit = println("""Syntax locality ICL — Scala 3
Commands: plan, audit, run, resume, score, report, preflight, freeze
plan --preset smoke|pilot|main --phase p0|p1|p2 --out DIR [--config FILE] [--model MODEL]
  p2: full-program parsing/generation smoke or pilot; use configs/full-program-pilot.json.
plan ... --count-tokens --execute --max-http-attempts N
audit|score|report --run DIR
run|resume --run DIR --mock
run|resume --run DIR --execute --max-generation-calls N --max-http-attempts N --local-token-cap N
  [--protocol-hash HASH] [--retry-ambiguous] [--soft-budget] [--allow-carried-reservations]
preflight --out DIR --execute --max-http-attempts N --local-token-cap N [--model MODEL]
freeze --run DIR --capabilities FILE
Every command supports --help. No --execute means zero HTTP calls; mock results are synthetic wiring fixtures.
""")
