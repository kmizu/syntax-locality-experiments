package locality.bench.run

import java.nio.file.{Files, Path}
import java.nio.charset.StandardCharsets.UTF_8
import java.time.{Instant, LocalDate, ZoneOffset}
import locality.llm.*
import locality.llm.json.*
import JsonSupport.*
import scala.util.control.NonFatal
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress

/** Offline capability-contract tests use synthetic fixtures and a local loopback server.
  * No real API key, environment loader, or external HTTP endpoint is used.
  */
object PreflightTests:
  private val model = "gpt-5.6-terra"
  private val countBody = "{ \"input_tokens\": 40, \"object\": \"response.input_tokens\", \"unknown\": true }"
  private val responseBody = """{"id":"resp_preflight","model":"fixture-returned-model","status":"completed","output":[{"type":"message","role":"assistant","content":[{"type":"output_text","text":"5916"}]}],"usage":{"input_tokens":40,"output_tokens":10,"total_tokens":50}}"""
  private val good = LlmResponse("resp_preflight","fixture-returned-model","completed","5916",Vector.empty,None,
    Some(TokenUsage(40,10,50,None,None,None)),Some("req_generation"),responseBody)
  private val countHttp = HttpResult(200,Map("x-request-id" -> Vector("req_count")),countBody)

  private final class FixtureClient(
    counted: Either[LlmError,Long] = Right(40L),
    generated: Either[LlmError,LlmResponse] = Right(good),
    diagnostics: Option[HttpResult] = Some(countHttp),
    crashDuringGenerate: Boolean = false
  ) extends LlmClient with CountDiagnostics:
    var counts = 0
    var generations = 0
    var requests = Vector.empty[(String,LlmRequest)]
    private var latest: Option[HttpResult] = None
    def countInputTokens(request: LlmRequest): Either[LlmError,Long] =
      counts += 1
      requests :+= "count" -> request
      latest = diagnostics
      counted
    def takeCountHttpResult(): Option[HttpResult] =
      val value = latest
      latest = None
      value
    def generate(request: LlmRequest): Either[LlmError,LlmResponse] =
      generations += 1
      requests :+= "generate" -> request
      if crashDuringGenerate then throw new IllegalStateException("Injected crash after dispatch")
      generated

  private def fresh(): Path = Files.createTempDirectory("synthetic-preflight-test-")
  private def read(dir: Path, name: String): String = Files.readString(dir.resolve(name),UTF_8)
  private def json(dir: Path, name: String): JsonValue = parse(read(dir,name))
  private def lines(dir: Path, name: String): Vector[JsonValue] =
    if !Files.exists(dir.resolve(name)) then Vector.empty
    else read(dir,name).split("\n").filter(_.nonEmpty).map(parse).toVector
  private def rejected(action: => Unit): Throwable =
    val failure = try
      action
      None
    catch case NonFatal(e) => Some(e)
    assert(failure.nonEmpty,"Expected preflight rejection")
    failure.get
  private def flags(dir: Path, count: Boolean, generation: Boolean): JsonValue =
    val value = json(dir,"capabilities.json")
    assert(str(value,"model") == model)
    assert(str(value,"reasoningEffort") == "low")
    assert(bool(field(value,"tokenCountSupported")) == count)
    assert(bool(field(value,"generationSupported")) == generation)
    assert(bool(field(value,"verified")) == generation)
    value
  private def assertUtcWindows(records: Vector[JsonValue]): Unit =
    assert(records.nonEmpty)
    val today = LocalDate.ofInstant(Instant.now,ZoneOffset.UTC)
    records.foreach { row =>
      val day = LocalDate.parse(str(row,"window"))
      assert(day == today || day == today.minusDays(1),"Ledger must retain the UTC budget window")
    }

  private def success(): Unit =
    val dir = fresh()
    val client = new FixtureClient()
    Preflight.run(dir,model,client,Limits(50,4,20000))
    assert(client.counts == 1 && client.generations == 1)
    assert(client.requests.map(_._1) == Vector("count","generate"))
    assert(client.requests.map(_._2).distinct.size == 1,"Count and generation must share model/instructions/input/settings")
    val request = client.requests.head._2
    assert(request.model == model && request.reasoningEffort.contains("low") && request.maxOutputTokens == 8192)
    assert(Json.canonical(json(dir,"request.json")) == Json.canonical(requestJson(request)),"Saved request must preserve all logical request fields")
    val capabilities = flags(dir,true,true)
    assert(str(capabilities,"generationResponseId") == "resp_preflight")
    assert(str(capabilities,"returnedModel") == "fixture-returned-model")
    assert(str(capabilities,"endpoint") == "https://api.openai.com/v1")
    assert(num(capabilities,"knownTokens") == 50 && num(capabilities,"unresolvedTokens") == 0 && num(capabilities,"httpAttempts") == 2)
    assert(read(dir,"count-attempt-001-body.txt") == countBody,"Count raw formatting and unknown fields must be preserved")
    val countMeta = json(dir,"count-attempt-001-meta.json")
    assert(num(countMeta,"httpStatus") == 200 && str(countMeta,"requestId") == "req_count")
    assert(str(countMeta,"redactionPolicy") == "known_secret_values_masked")
    assert(num(json(dir,"token-count.json"),"input_tokens") == 40)
    assert(read(dir,"attempt-001-body.txt") == responseBody)
    val generationMeta = json(dir,"attempt-001-meta.json")
    assert(str(generationMeta,"responseId") == "resp_preflight" && str(generationMeta,"requestId") == "req_generation")
    assert(num(field(generationMeta,"usage"),"totalTokens") == 50)
    val ledger = lines(dir,"usage-ledger.jsonl")
    assert(ledger.map(str(_,"kind")) == Vector("reserve","settle"))
    assert(ledger.map(str(_,"reservationId")).distinct == Vector("preflight/1"))
    assert(num(ledger.head,"tokens") == 8488 && num(ledger.last,"tokens") == 50)
    assertUtcWindows(ledger)
    assert(lines(dir,"events.jsonl").count(row => str(row,"kind") == "dispatch") == 1)

  private def failedCount(): Unit =
    val dir = fresh()
    val raw = """{"error":{"code":"rate_limit_exceeded","message":"fixture count failure"}}"""
    val error = ApiError(429,Some("rate_limit_exceeded"),"fixture count failure",Some("2"),Some("req_failed_count"),raw)
    val client = new FixtureClient(Left(error),diagnostics=Some(HttpResult(429,Map("x-request-id"->Vector("req_failed_count")),raw)))
    rejected(Preflight.run(dir,model,client,Limits(1,4,20000)))
    assert(client.counts == 1 && client.generations == 0)
    flags(dir,false,false)
    assert(read(dir,"count-attempt-001-body.txt") == raw)
    assert(num(json(dir,"count-attempt-001-meta.json"),"httpStatus") == 429)
    assert(str(json(dir,"count-attempt-001-meta.json"),"requestId") == "req_failed_count")
    assert(lines(dir,"usage-ledger.jsonl").isEmpty,"Failed count must not reserve generation tokens")
    assert(lines(dir,"events.jsonl").forall(row => str(row,"kind") != "dispatch"),"Failed count must not record generation dispatch")
    assert(!Files.exists(dir.resolve("attempt-001-body.txt")))

  private def failedGeneration(): Unit =
    val dir = fresh()
    val raw = """{"error":{"code":"server_error","message":"fixture generation failure"}}"""
    val error = ApiError(503,Some("server_error"),"fixture generation failure",None,Some("req_failed_generation"),raw)
    val client = new FixtureClient(generated=Left(error))
    rejected(Preflight.run(dir,model,client,Limits(1,4,20000)))
    assert(client.counts == 1 && client.generations == 1,"Preflight must not hide a retry or model fallback")
    val capabilities = flags(dir,true,false)
    assert(num(capabilities,"unresolvedReservation") == 8488)
    assert(read(dir,"attempt-001-body.txt") == raw)
    val meta = json(dir,"attempt-001-meta.json")
    assert(num(meta,"httpStatus") == 503 && str(meta,"requestId") == "req_failed_generation")
    val ledger = lines(dir,"usage-ledger.jsonl")
    assert(ledger.size == 1 && str(ledger.head,"kind") == "reserve" && num(ledger.head,"tokens") == 8488)
    assertUtcWindows(ledger)

  private def unknownUsage(): Unit =
    val dir = fresh()
    val raw = """{"id":"resp_preflight","model":"fixture-returned-model","status":"completed","output":[{"type":"message","role":"assistant","content":[{"type":"output_text","text":"5916"}]}],"usage":null}"""
    val client = new FixtureClient(generated=Right(good.copy(usage=None,rawBody=raw)))
    rejected(Preflight.run(dir,model,client,Limits(1,4,20000)))
    val capabilities = flags(dir,true,false)
    assert(num(capabilities,"knownTokens") == 0 && num(capabilities,"unresolvedTokens") == 8488)
    assert(read(dir,"attempt-001-body.txt") == raw)
    val ledger = lines(dir,"usage-ledger.jsonl")
    assert(ledger.size == 1 && str(ledger.head,"kind") == "reserve","Unknown usage must not become a zero-token settlement")
    assertUtcWindows(ledger)

  private def capBeforeNetwork(): Unit =
    val dir = fresh()
    val client = new FixtureClient()
    rejected(Preflight.run(dir,model,client,Limits(1,4,8447)))
    assert(client.counts == 0 && client.generations == 0,"Impossible output reservation must be rejected before token-count HTTP")
    assert(lines(dir,"usage-ledger.jsonl").isEmpty)
    assert(lines(dir,"events.jsonl").isEmpty)

  private def insufficientHttpAllowance(): Unit =
    val dir = fresh()
    val client = new FixtureClient()
    rejected(Preflight.run(dir,model,client,Limits(1,1,20000)))
    assert(client.counts == 0 && client.generations == 0,"Count plus generation requires at least two HTTP attempts")
    assert(lines(dir,"usage-ledger.jsonl").isEmpty,"A definitely undispatched request must not leave an unexplained reservation")

  private def crashAndReuse(): Unit =
    val dir = fresh()
    val crashing = new FixtureClient(crashDuringGenerate=true)
    rejected(Preflight.run(dir,model,crashing,Limits(1,4,20000)))
    assert(crashing.counts == 1 && crashing.generations == 1)
    assert(!Files.exists(dir.resolve("capabilities.json")),"Injected crash is before capability finalization")
    val originalLedger = read(dir,"usage-ledger.jsonl")
    val ledger = lines(dir,"usage-ledger.jsonl")
    assert(ledger.size == 1 && num(ledger.head,"tokens") == 8488)
    assertUtcWindows(ledger)
    assert(lines(dir,"events.jsonl").count(row => str(row,"kind") == "dispatch") == 1)
    val restarted = new FixtureClient()
    rejected(Preflight.run(dir,model,restarted,Limits(1,4,20000)))
    assert(restarted.counts == 0 && restarted.generations == 0,"A crashed preflight directory must not reset its budget and send again")
    assert(read(dir,"usage-ledger.jsonl") == originalLedger)

  private def refuseExistingArtifacts(): Unit =
    Vector("request.json" -> "{}", "count-attempt-001-request.json" -> "{}", "events.jsonl" -> "{\"kind\":\"dispatch\"}\n", "usage-ledger.jsonl" -> "{\"kind\":\"reserve\",\"tokens\":8488}\n").foreach { (name,body) =>
      val dir = fresh()
      Files.writeString(dir.resolve(name),body,UTF_8)
      val client = new FixtureClient()
      rejected(Preflight.run(dir,model,client,Limits(1,4,20000)))
      assert(client.counts == 0 && client.generations == 0,s"Existing $name must cause refusal before HTTP")
      assert(read(dir,name) == body,s"Rejected preflight must preserve existing $name")
    }

  private def observedUsageOverCap(): Unit =
    val dir = fresh()
    val raw = """{"id":"resp_preflight","model":"fixture-returned-model","status":"completed","output":[{"type":"message","role":"assistant","content":[{"type":"output_text","text":"5916"}]}],"usage":{"input_tokens":40,"output_tokens":9000,"total_tokens":9040}}"""
    val client = new FixtureClient(generated=Right(good.copy(usage=Some(TokenUsage(40,9000,9040,None,None,None)),rawBody=raw)))
    rejected(Preflight.run(dir,model,client,Limits(1,4,8500)))
    val capabilities = flags(dir,true,false)
    assert(num(capabilities,"knownTokens") == 9040,"Observed usage must be recorded even when it exceeds the configured cap")
    assert(num(capabilities,"unresolvedTokens") == 0)
    assert(read(dir,"attempt-001-body.txt") == raw)
    assert(num(lines(dir,"usage-ledger.jsonl").last,"tokens") == 9040)

  private def cliOfflineGuards(): Unit =
    val out = fresh().resolve("never-created")
    locality.bench.cli.Main.dispatch(Vector("preflight","--out",out.toString))
    assert(!Files.exists(out),"Dry preflight must not create a run or read credentials")
    rejected(locality.bench.cli.Main.dispatch(Vector("preflight","--out",out.toString,"--local-token-cap","-1")))
    rejected(locality.bench.cli.Main.dispatch(Vector("preflight","--out",out.toString,"--mock")))
    assert(!Files.exists(out))

  private def persistedWireBodies(): Unit =
    val dir = fresh()
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0)
    val received = new java.util.concurrent.ConcurrentHashMap[String,Array[Byte]]()
    val persistedBeforeSend = new java.util.concurrent.atomic.AtomicBoolean(true)
    server.createContext("/v1/responses", exchange =>
      val path = exchange.getRequestURI.getPath
      val savedName = if path.endsWith("/input_tokens") then "count-attempt-001-request.json" else "request.json"
      if !Files.exists(dir.resolve(savedName)) then persistedBeforeSend.set(false)
      received.put(path,exchange.getRequestBody.readAllBytes())
      val bytes = (if path.endsWith("/input_tokens") then countBody else responseBody).getBytes(UTF_8)
      exchange.sendResponseHeaders(200,bytes.length)
      try exchange.getResponseBody.write(bytes)
      finally exchange.close()
    )
    server.start()
    try
      val client = new OpenAiClient(OpenAiConfig("synthetic-fixture-key",s"http://127.0.0.1:${server.getAddress.getPort}/v1"))
      Preflight.run(dir,model,client,Limits(1,4,20000))
      assert(received.size == 2,"Preflight must send one count and one generation without retries")
      val countFile = dir.resolve("count-attempt-001-request.json")
      assert(Files.exists(countFile),"Preflight must persist its distinct token-count request before HTTP")
      assert(persistedBeforeSend.get(),"Both request bodies must be saved before their HTTP dispatch")
      val savedCount = Files.readAllBytes(countFile)
      val savedGeneration = Files.readAllBytes(dir.resolve("request.json"))
      assert(java.util.Arrays.equals(savedCount,received.get("/v1/responses/input_tokens")),"Saved count request must equal exact transmitted UTF-8 bytes")
      assert(java.util.Arrays.equals(savedGeneration,received.get("/v1/responses")),"Saved generation request must equal exact transmitted UTF-8 bytes")
      val countJson = parse(new String(savedCount,UTF_8))
      assert(obj(countJson).fields.map(_._1).toSet == Set("model","instructions","input"))
      assert(field(countJson,"input") == field(json(dir,"request.json"),"input"))
      assert(str(countJson,"instructions") == str(json(dir,"request.json"),"instructions"))
      assert(read(dir,"count-attempt-001-body.txt") == countBody && read(dir,"attempt-001-body.txt") == responseBody)
    finally server.stop(0)

  private def absentReasoningRoundTrip(): Unit =
    val request = LlmRequest(model,"日本語 😀",Vector(TextMessage("user","first"),TextMessage("assistant","second")),None,8192)
    val saved = Json.canonical(requestJson(request))
    assert(!obj(parse(saved)).fields.exists(_._1 == "reasoning"),"Absent effort must stay absent in persisted and transmitted requests")
    assert(requestFrom(parse(saved)) == request,"Saved optional effort must not silently become low")

  def run(): Unit =
    val tests: Vector[(String,() => Unit)] = Vector(
      "success artifacts and capability contract" -> (() => success()),
      "failed count stops generation and reservation" -> (() => failedCount()),
      "failed generation keeps unresolved usage" -> (() => failedGeneration()),
      "unknown usage never settles as zero" -> (() => unknownUsage()),
      "impossible cap refuses before network" -> (() => capBeforeNetwork()),
      "insufficient HTTP allowance refuses before count" -> (() => insufficientHttpAllowance()),
      "crash reservation survives and same directory cannot restart" -> (() => crashAndReuse()),
      "existing partial artifacts are preserved and refused" -> (() => refuseExistingArtifacts()),
      "actual usage over cap invalidates capability" -> (() => observedUsageOverCap()),
      "persisted count and generation bodies equal actual loopback wire bytes" -> (() => persistedWireBodies()),
      "request serialization preserves absent reasoning effort" -> (() => absentReasoningRoundTrip()),
      "CLI dry and invalid options stay offline" -> (() => cliOfflineGuards())
    )
    val failures = Vector.newBuilder[String]
    tests.foreach { (name,test) =>
      try
        test()
        println(s"PASS preflight: $name")
      catch case NonFatal(e) =>
        val failure = s"$name: ${e.getClass.getSimpleName}: ${e.getMessage}"
        failures += failure
        println(s"FAIL preflight: $failure")
    }
    val failed = failures.result()
    assert(failed.isEmpty,failed.mkString("\n"))
  def main(args: Array[String]): Unit =
    require(args.isEmpty,"PreflightTests accepts no arguments")
    run()
