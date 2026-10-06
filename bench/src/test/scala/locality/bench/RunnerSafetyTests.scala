package locality.bench

import locality.bench.run.*
import locality.llm.*
import locality.llm.json.*
import locality.bench.run.JsonSupport.*
import java.nio.file.{Files, Path}
import java.time.Instant
import java.util.concurrent.{ConcurrentHashMap, CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}

object RunnerSafetyTests:
  private final class FakeClock(start: String = "2026-10-05T12:00:00Z") extends RunClock:
    private val current = new AtomicLong(Instant.parse(start).toEpochMilli)
    def millis: Long = current.get()
    def sleep(millis: Long): Unit = { current.addAndGet(millis); () }

  private def completed(request: LlmRequest, unknownUsage: Boolean = false): LlmResponse =
    val full = Json.obj("id" -> s("fixture"), "model" -> s(request.model), "status" -> s("completed"),
      "output" -> Arr(Vector(Json.obj("type" -> s("message"), "role" -> s("assistant"),
        "content" -> Arr(Vector(Json.obj("type" -> s("output_text"), "text" -> s("wrong-answer"))))))),
      "usage" -> Json.obj("input_tokens" -> n(100), "output_tokens" -> n(1), "total_tokens" -> n(101)))
    val raw = if unknownUsage then Obj(full.fields.filterNot(_._1 == "usage")) else full
    LlmResponse("fixture", request.model, "completed", "wrong-answer", Vector.empty, None,
      if unknownUsage then None else Some(TokenUsage(100, 1, 101, None, None, None)), None, Json.canonical(raw))
  private val rateLimit = ApiError(429, Some("rate_limit_exceeded"), "rate limit", Some("0"), None,
    "{\"error\":{\"message\":\"rate limit\",\"type\":\"rate_limit_error\",\"code\":\"rate_limit_exceeded\"}}")

  private final class ScriptClient(countFailures: Int = 0, generationFailures: Int = 0,
    ambiguous: Boolean = false, rendezvous: Boolean = false, unknownUsage: Boolean = false) extends LlmClient:
    val counts = new AtomicInteger(0)
    val generations = new AtomicInteger(0)
    val maximumConcurrency = new AtomicInteger(0)
    private val active = new AtomicInteger(0)
    private val perCount = new ConcurrentHashMap[String, AtomicInteger]()
    private val perGeneration = new ConcurrentHashMap[String, AtomicInteger]()
    private val firstWorkers = new CountDownLatch(4)
    def countsFor(requestHash: String): Int = Option(perCount.get(requestHash)).map(_.get()).getOrElse(0)
    def generationsFor(requestHash: String): Int = Option(perGeneration.get(requestHash)).map(_.get()).getOrElse(0)
    private def during[A](body: => A): A =
      val count = active.incrementAndGet()
      maximumConcurrency.accumulateAndGet(count, (a, b) => math.max(a, b))
      try body finally active.decrementAndGet()
    private def next(map: ConcurrentHashMap[String, AtomicInteger], request: LlmRequest): Int =
      map.computeIfAbsent(hash(requestJson(request)), _ => new AtomicInteger(0)).incrementAndGet()
    def countInputTokens(request: LlmRequest): Either[LlmError, Long] = during {
      counts.incrementAndGet()
      if rendezvous then
        firstWorkers.countDown()
        assert(firstWorkers.await(3, TimeUnit.SECONDS), "four workers must be available concurrently")
      if next(perCount, request) <= countFailures then Left(rateLimit) else Right(100L)
    }
    def generate(request: LlmRequest): Either[LlmError, LlmResponse] = during {
      generations.incrementAndGet()
      val attempt = next(perGeneration, request)
      if ambiguous then Left(TransportError("fixture connection lost after dispatch", true))
      else if attempt <= generationFailures then Left(rateLimit)
      else Right(completed(request, unknownUsage))
    }

  private def config(overrides: (String, JsonValue)*): Path =
    val raw = obj(parse(Files.readString(Path.of("configs/smoke.json"))))
    val value = Obj(raw.fields.filterNot(p => overrides.exists(_._1 == p._1)) ++ overrides)
    val file = Files.createTempFile("locality-safety-config", ".json")
    Files.writeString(file, Json.canonical(value))
    file
  private def withPlan(replicates: Int = 1, concurrency: Int = 4)(body: RunStore => Unit): Unit =
    val out = Files.createTempDirectory("locality-runner-safety")
    val conf = config("replicates" -> n(replicates), "concurrency" -> n(concurrency), "maxRpm" -> n(100000), "maxTpm" -> n(10000000))
    Planner.save(Protocol.load("smoke", "p0", Some(conf)), out)
    val store = new RunStore(out)
    try body(store) finally store.close()

  def run(): Unit =
    aggregateAttemptLimit()
    attemptLimitAcrossResume()
    logicalDeadline()
    utcWindowRollover()
    generationAndHttpCaps()
    tokenCapWithUnknownUsage()
    wrongAnswerAfterRetry()
    ambiguityOnResume()
    dispatchCrashOnResume()
    replicateIsNotCache()
    generationGridBounds()
    cancelledThrottleDoesNotCountHttp()
    retryHttpCapPreservesDispatch()
    reportCrashAccounting()
    cancelledRetryKeepsKnownError()

  def reportCrashAccounting(): Unit = withPlan(concurrency = 1) { store =>
    val id = str(store.jsonLines("schedule.jsonl").head, "id")
    val request = requestFrom(parse(store.read(s"trials/$id/request.json")))
    val response = completed(request)
    store.append("events.jsonl", Json.canonical(Json.obj("kind" -> s("token_count_attempt"), "trialId" -> s(id), "attempt" -> n(1), "time" -> s("2026-10-05T12:00:00Z"))))
    store.append("events.jsonl", Json.canonical(Json.obj("kind" -> s("dispatch"), "trialId" -> s(id), "attempt" -> n(1), "time" -> s("2026-10-05T12:00:00Z"))))
    store.atomic(s"trials/$id/attempt-001-body.txt", response.rawBody)
    store.atomic(s"trials/$id/attempt-001-meta.json", Json.canonical(Json.obj("usage" -> Runner.usageJson(response.usage))))
    val row = Results.observations(store).find(_.trialId == id).get
    assert(row.dispatched && !row.terminal && row.httpAttempts == 2 && row.generationAttempts == 1,
      "Reporting before resume must retain durable dispatched attempt counts")
    assert(row.usage.flatMap(_.inputTokens).contains(100L) && row.attemptUsage.size == 1,
      "Reporting before resume must retain saved known usage")
  }

  def cancelledRetryKeepsKnownError(): Unit = withPlan(concurrency = 1) { store =>
    val client = new LlmClient:
      def countInputTokens(request: LlmRequest) = Right(100L)
      def generate(request: LlmRequest) = Left(rateLimit.copy(retryAfter = Some("450")))
    Runner.execute(store, client, Limits(1, 8, 100000), execute = true, mock = true, clock = new FakeClock)
    val id = str(store.jsonLines("schedule.jsonl").head, "id")
    assert(store.terminal(id).exists(t => str(t, "status") == "api_rejected" && num(t, "generationAttempts") == 1),
      "A known rejection must remain terminal after backoff crosses admission deadline")
  }

  def retryHttpCapPreservesDispatch(): Unit = withPlan(concurrency = 1) { store =>
    val client = new ScriptClient(generationFailures = 1)
    Runner.execute(store, client, Limits(1, 2, 100000), execute = true, mock = true, clock = new FakeClock)
    val id = str(store.jsonLines("schedule.jsonl").head, "id")
    val terminal = store.terminal(id).get
    assert(str(terminal, "status") != "not_dispatched", "A dispatched retry failure is infrastructure missingness")
    assert(num(terminal, "generationAttempts") == 1)
  }

  def cancelledThrottleDoesNotCountHttp(): Unit =
    val out = Files.createTempDirectory("locality-http-cancel")
    val conf = config("concurrency" -> n(1), "maxRpm" -> n(1))
    Planner.save(Protocol.load("smoke", "p0", Some(conf)), out)
    val store = new RunStore(out)
    try
      val client = new ScriptClient()
      val result = Runner.execute(store, client, Limits(1, 10, 100000), execute = true,
        clock = new FakeClock("2026-10-05T23:59:30Z"))
      assert(client.counts.get() == 1 && client.generations.get() == 0)
      assert(result.httpAttempts == 1, "Throttle cancellation before dispatch is not an HTTP attempt")
      val id = str(store.jsonLines("schedule.jsonl").head, "id")
      assert(num(store.terminal(id).get, "generationAttempts") == 0, "No generation was dispatched")
    finally store.close()

  def aggregateAttemptLimit(): Unit = withPlan() { store =>
    val client = new ScriptClient(countFailures = 3)
    val result = Runner.execute(store, client, Limits(1, 8, 100000), execute = true, mock = true, clock = new FakeClock)
    assert(result.httpAttempts <= 4 && client.counts.get() + client.generations.get() <= 4,
      "one logical trial must not exceed four aggregate count/generate HTTP attempts")
  }

  def attemptLimitAcrossResume(): Unit = withPlan() { store =>
    val id = str(store.jsonLines("schedule.jsonl").head, "id")
    val requestHash = hash(parse(store.read(s"trials/$id/request.json")))
    val client = new ScriptClient(countFailures = 3)
    val clock = new FakeClock
    Runner.execute(store, client, Limits(1, 8, 100000), execute = true, mock = true, clock = clock)
    Runner.execute(store, client, Limits(1, 8, 100000), execute = true, mock = true, clock = clock)
    assert(client.countsFor(requestHash) + client.generationsFor(requestHash) <= 4,
      "resume must not reset the same trial's aggregate HTTP-attempt limit")
  }

  def logicalDeadline(): Unit = withPlan() { store =>
    val clock = new FakeClock
    val generated = new AtomicInteger(0)
    val client = new LlmClient:
      def countInputTokens(request: LlmRequest): Either[LlmError, Long] =
        clock.sleep(600001)
        Right(100L)
      def generate(request: LlmRequest): Either[LlmError, LlmResponse] =
        generated.incrementAndGet()
        Right(completed(request))
    Runner.execute(store, client, Limits(1, 8, 100000), execute = true, mock = true, clock = clock)
    assert(generated.get() == 0, "generation must not start after the 600 second logical deadline")
  }

  def utcWindowRollover(): Unit = withPlan(concurrency = 1) { store =>
    val clock = new FakeClock("2026-10-05T23:59:59Z")
    val generated = new AtomicInteger(0)
    val client = new LlmClient:
      def countInputTokens(request: LlmRequest): Either[LlmError, Long] = Right(100L)
      def generate(request: LlmRequest): Either[LlmError, LlmResponse] =
        if generated.incrementAndGet() == 1 then clock.sleep(2000)
        Right(completed(request))
    Runner.execute(store, client, Limits(5, 20, 1000000), execute = true, mock = true, clock = clock)
    val reserved = store.jsonLines("usage-ledger.jsonl").filter(row => str(row, "kind") == "reserve").map(row => str(row, "reservationId") -> str(row, "window")).toMap
    val dispatches = store.jsonLines("events.jsonl").filter(row => str(row, "kind") == "dispatch")
    assert(dispatches.forall(row => str(row, "time").take(10) == reserved(str(row, "reservationId"))),
      "new-day dispatch must use its UTC budget window or stop at rollover")
  }

  def generationAndHttpCaps(): Unit =
    withPlan() { store =>
      val client = new ScriptClient(rendezvous = true)
      val result = Runner.execute(store, client, Limits(20, 7, 1000000), execute = true, mock = true, clock = new FakeClock)
      assert(result.httpAttempts <= 7 && client.counts.get() + client.generations.get() <= 7, "count and generation share the HTTP cap")
      assert(client.maximumConcurrency.get() == 4, "real runner uses four bounded workers")
    }
    withPlan() { store =>
      val client = new ScriptClient()
      val result = Runner.execute(store, client, Limits(3, 20, 1000000), execute = true, mock = true, clock = new FakeClock)
      assert(result.logicalCalls == 3 && client.generations.get() == 3, "logical call cap applies under concurrency four")
    }

  def tokenCapWithUnknownUsage(): Unit = withPlan() { store =>
    val client = new ScriptClient(rendezvous = true, unknownUsage = true)
    val reservation = 100L + 8192L + 256L
    val result = Runner.execute(store, client, Limits(20, 40, 2 * reservation), execute = true, mock = true, clock = new FakeClock)
    assert(result.logicalCalls == 2 && client.generations.get() == 2 && result.unresolvedTokens == 2 * reservation,
      "unknown usage must retain two reservations and prevent concurrent overspend")
    assert(result.knownTokens == 0, "unknown usage is not converted to zero known token use")
  }

  def wrongAnswerAfterRetry(): Unit = withPlan() { store =>
    val client = new ScriptClient(generationFailures = 1)
    val result = Runner.execute(store, client, Limits(5, 30, 1000000), execute = true, mock = true, clock = new FakeClock)
    assert(result.logicalCalls == 5 && client.counts.get() == 5 && client.generations.get() == 10,
      "one 429 retry followed by completed wrong answer must stop each logical trial")
    val terminal = store.jsonLines("schedule.jsonl").take(5).map(row => parse(store.read(s"trials/${str(row, "id")}/terminal.json")))
    assert(terminal.forall(row => str(row, "status") == "completed" && str(row, "text") == "wrong-answer"))
  }

  def ambiguityOnResume(): Unit = withPlan() { store =>
    val client = new ScriptClient(ambiguous = true)
    val clock = new FakeClock
    val reservation = 100L + 8192L + 256L
    val first = Runner.execute(store, client, Limits(1, 10, reservation), execute = true, mock = true, clock = clock)
    assert(first.unresolvedTokens == reservation && client.generations.get() == 1, "ambiguous response retains full reservation")
    val second = Runner.execute(store, client, Limits(1, 10, reservation), execute = true, mock = true, clock = clock)
    assert(second.unresolvedTokens == reservation && second.logicalCalls == 0 && client.generations.get() == 1,
      "resume retains reservation and never resends terminal ambiguous trial")
    assert(store.jsonLines("usage-ledger.jsonl").count(row => str(row, "kind") == "reserve") == 1)
  }

  def dispatchCrashOnResume(): Unit = withPlan() { store =>
    val id = str(store.jsonLines("schedule.jsonl").head, "id")
    val reservation = 100L + 8192L + 256L
    store.append("usage-ledger.jsonl", Json.canonical(Json.obj("kind" -> s("reserve"), "reservationId" -> s(s"$id/1"),
      "trialId" -> s(id), "tokens" -> n(reservation), "window" -> s("2026-10-05"))))
    store.append("events.jsonl", Json.canonical(Json.obj("kind" -> s("dispatch"), "trialId" -> s(id), "attempt" -> n(1))))
    val client = new ScriptClient()
    val result = Runner.execute(store, client, Limits(1, 10, reservation), execute = true, mock = true, clock = new FakeClock)
    assert(client.generations.get() == 0 && result.unresolvedTokens == reservation, "dispatch crash must preserve reservation and forbid duplicate generation")
    assert(str(parse(store.read(s"trials/$id/terminal.json")), "status") == "ambiguous_outcome", "missing terminal after dispatch is recovered as ambiguous")
  }

  def replicateIsNotCache(): Unit = withPlan(2) { store =>
    val client = new ScriptClient()
    val result = Runner.execute(store, client, Limits(40, 100, 1000000), execute = true, mock = true, clock = new FakeClock)
    assert(result.logicalCalls == 40 && client.generations.get() == 40, "replicate two must generate forty responses")
    assert(client.counts.get() == 20, "only token counts may be reused by identical prompt hash")
  }

  def generationGridBounds(): Unit =
    def rejected(file: Path): Boolean =
      try { Planner.build(Protocol.load("pilot", "p1", Some(file))); false }
      catch case _: IllegalArgumentException => true
    assert(rejected(config("depths" -> Arr(Vector(n(16))))), "T3 P1 depth above eight must be rejected")
    assert(rejected(config("depths" -> Arr(Vector(n(2))), "fillersGeneration" -> Arr(Vector(n(64))))), "T3 P1 filler above thirty-two must be rejected")

  def main(args: Array[String]): Unit =
    if args.isEmpty then run()
    else args.head match
      case "attempts" => aggregateAttemptLimit()
      case "attempts-resume" => attemptLimitAcrossResume()
      case "deadline" => logicalDeadline()
      case "utc-window" => utcWindowRollover()
      case "caps" => generationAndHttpCaps()
      case "token-cap" => tokenCapWithUnknownUsage()
      case "wrong" => wrongAnswerAfterRetry()
      case "ambiguity" => ambiguityOnResume()
      case "crash" => dispatchCrashOnResume()
      case "replicates" => replicateIsNotCache()
      case "generation-grid" => generationGridBounds()
      case other => throw new IllegalArgumentException(other)
