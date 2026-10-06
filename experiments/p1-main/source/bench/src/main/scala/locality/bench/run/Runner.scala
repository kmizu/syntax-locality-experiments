package locality.bench.run

import locality.llm.*
import locality.llm.json.*
import JsonSupport.*
import java.time.{Instant, LocalDate, ZoneOffset}
import java.util.concurrent.{Executors, Callable, TimeUnit}
import java.util.concurrent.atomic.AtomicBoolean

trait RunClock:
  def millis: Long
  def sleep(millis: Long): Unit
object SystemRunClock extends RunClock:
  def millis: Long = System.currentTimeMillis()
  def sleep(millis: Long): Unit = Thread.sleep(millis)

final class Throttle(rpm: Int, tpm: Int, clock: RunClock):
  private val requests = scala.collection.mutable.ArrayBuffer.empty[Long]
  private val tokens = scala.collection.mutable.ArrayBuffer.empty[(Long, Long)]
  def acquire(reservedTokens: Long, cancelled: () => Boolean = () => false): Boolean = synchronized {
    require(reservedTokens <= tpm, "Single request exceeds local TPM setting")
    var admitted = false
    while !admitted && !cancelled() do
      val now = clock.millis
      requests.filterInPlace(_ > now - 60000)
      tokens.filterInPlace(_._1 > now - 60000)
      if requests.size < rpm && tokens.map(_._2).sum + reservedTokens <= tpm then
        requests += now
        if reservedTokens > 0 then tokens += now -> reservedTokens
        admitted = true
      else
        val delays = Vector(if requests.size >= rpm then Some(requests.head + 60000 - now) else None,
          if tokens.map(_._2).sum + reservedTokens > tpm then Some(tokens.head._1 + 60000 - now) else None).flatten
        clock.sleep(Math.min(1000, Math.max(1, delays.min)))
    admitted && !cancelled()
  }

final case class RunResult(logicalCalls: Int, httpAttempts: Int, knownTokens: Long, unresolvedTokens: Long)

object Runner:
  def execute(store: RunStore, client: LlmClient, limits: Limits, execute: Boolean = false,
    mock: Boolean = false, retryAmbiguous: Boolean = false, softBudget: Boolean = false,
    clock: RunClock = SystemRunClock, allowCarriedReservations: Boolean = false,
    protocolHash: Option[String] = None): RunResult =
    if !execute && !mock then
      println("Plan only; zero HTTP requests. Add --execute and all three limits for live execution.")
      return RunResult(0, 0, 0, 0)
    Planner.audit(store)
    val p = Protocol.from(parse(store.read("protocol.json")))
    if execute && !mock && p.preset == "main" then
      require(protocolHash.exists(h => Freeze.verify(store, h)), "Main live requires unchanged freeze and explicit matching protocol hash")
    val mode = Json.obj("synthetic_mock" -> Bool(mock))
    if store.exists("execution-mode.json") then require(parse(store.read("execution-mode.json")) == mode, "Cannot mix mock and live results")
    else store.atomic("execution-mode.json", Json.canonical(mode))
    val window = LocalDate.ofInstant(Instant.ofEpochMilli(clock.millis), ZoneOffset.UTC).toString
    val ledger = store.jsonLines("usage-ledger.jsonl")
    var known = 0L
    var unresolved = Map.empty[String, (Long, String)]
    ledger.foreach { row =>
      str(row, "kind") match
        case "reserve" => unresolved += str(row, "reservationId") -> (num(row, "tokens"), str(row, "window"))
        case "settle" =>
          unresolved -= str(row, "reservationId")
          if str(row, "window") == window then known = Math.addExact(known, num(row, "tokens"))
        case other => throw new IllegalArgumentException(s"Unknown ledger record $other")
    }
    val carried = unresolved.values.filter(_._2 != window).map(_._1).sum
    require(carried == 0 || allowCarriedReservations, s"$carried unresolved tokens carried from prior UTC window; explicit --allow-carried-reservations and sufficient new cap required")
    if carried > 0 then store.append("events.jsonl", Json.canonical(Json.obj("kind" -> s("carried_reservations_approved"), "tokens" -> n(carried), "window" -> s(window))))
    val budget = new Budget(limits, known, unresolved.toVector.map((key, entry) => key -> entry._1))
    val budgetFile = s"budget-$window.json"
    if store.exists(budgetFile) then
      val old = num(parse(store.read(budgetFile)), "localTokenCap")
      if old != limits.localTokenCap then store.append("events.jsonl", Json.canonical(Json.obj("kind" -> s("cap_changed"), "old" -> n(old), "new" -> n(limits.localTokenCap), "window" -> s(window))))
    store.atomic(budgetFile, Json.canonical(Json.obj("localTokenCap" -> n(limits.localTokenCap), "knownUsage" -> n(known), "carriedUnresolved" -> n(carried))))
    val priorEvents = store.jsonLines("events.jsonl")
    val priorCountEvents = priorEvents.filter(row => Set("token_count_attempt", "planning_token_count_attempt").contains(optionalString(row, "kind").getOrElse(""))).groupBy(row => str(row, "trialId"))
    val dispatched = priorEvents.filter(row => optionalString(row, "kind").contains("dispatch")).map(row => str(row, "trialId")).toSet
    val schedule = store.jsonLines("schedule.jsonl").map(row => str(row, "id"))
    schedule.foreach(store.terminal)
    dispatched.filter(id => !store.exists(s"trials/$id/terminal.json")).foreach { id =>
      val dispatchRows = priorEvents.filter(row => optionalString(row, "kind").contains("dispatch") && optionalString(row, "trialId").contains(id))
      val attempts = dispatchRows.map(row => num(row, "attempt").toInt).max
      val countAttempts = priorCountEvents.getOrElse(id, Vector.empty).size
      val body = f"trials/$id/attempt-$attempts%03d-body.txt"
      val savedResponse = if store.exists(body) then ResponseDecoder.decode(store.read(body), None).toOption else None
      terminal(store, id, "ambiguous_outcome", "", savedResponse, clock.millis, clock.millis, countAttempts, attempts, "Recovered dispatch without terminal; unresolved reservation retained")
    }
    val pendingBlocks = schedule.grouped(5).map(_.filter(id => !store.exists(s"trials/$id/terminal.json"))).filter(_.nonEmpty).toVector
    var remainingCalls = limits.maxGenerationCalls
    val selectedBlocks = pendingBlocks.takeWhile { block =>
      val fits = block.size <= remainingCalls || remainingCalls == limits.maxGenerationCalls && remainingCalls < 5
      if fits then remainingCalls -= Math.min(block.size, remainingCalls)
      fits
    }.map(block => if limits.maxGenerationCalls < 5 then block.take(limits.maxGenerationCalls) else block)
    val throttle = new Throttle(if mock then Int.MaxValue else p.integer("maxRpm"), if mock then Int.MaxValue else p.integer("maxTpm"), clock)
    val stopped = new AtomicBoolean(false)
    val pool = Executors.newFixedThreadPool(p.integer("concurrency"))
    val shutdownHook = new Thread(() => { stopped.set(true); pool.shutdown(); pool.awaitTermination(190, TimeUnit.SECONDS); () })
    Runtime.getRuntime.addShutdownHook(shutdownHook)
    val submittedAt = clock.millis
    try
      selectedBlocks.foreach { block =>
        val futures = block.map { id => pool.submit(new Callable[Unit]:
          def call(): Unit =
            if !stopped.get() then
              val request = requestFrom(parse(store.read(s"trials/$id/request.json")))
              val queueWait = clock.millis - submittedAt
              val start = priorCountEvents.get(id).flatMap(_.find(row => optionalString(row, "kind").contains("token_count_attempt"))).flatMap(row => optionalString(row, "time")).map(Instant.parse(_).toEpochMilli).getOrElse(clock.millis)
              def cancelled(): Boolean =
                if LocalDate.ofInstant(Instant.ofEpochMilli(clock.millis), ZoneOffset.UTC).toString != window then stopped.set(true)
                stopped.get() || clock.millis - start > 420000
              var countTime = 0L
              var countAttempts = priorCountEvents.getOrElse(id, Vector.empty).size
              val requestHash = hash(requestJson(request))
              val cacheName = s"token-count-cache/$requestHash.json"
              val counted: Either[LlmError, Long] = if store.exists(cacheName) then Right(num(parse(store.read(cacheName)), "input_tokens"))
                else
                  var result: Either[LlmError, Long] = Left(TransportError("HTTP attempt limit", false))
                  var retry = true
                  while retry && !cancelled() && countAttempts < 4 do
                    retry = false
                    if budget.httpAvailable && throttle.acquire(0, () => cancelled()) && !cancelled() && budget.http() then
                      countAttempts += 1
                      val begin = clock.millis
                      val countBase = f"trials/$id/count-attempt-$countAttempts%03d"
                      store.atomic(countBase + "-request.json", ResponsesRequestJson.countInputTokensBody(request))
                      store.append("events.jsonl", Json.canonical(Json.obj("kind" -> s("token_count_attempt"), "trialId" -> s(id), "attempt" -> n(countAttempts), "time" -> s(Instant.ofEpochMilli(begin).toString))))
                      result = client.countInputTokens(request)
                      client match
                        case diagnostics: CountDiagnostics => diagnostics.takeCountHttpResult().foreach { http =>
                          store.atomic(countBase + "-body.txt", http.body)
                          store.atomic(countBase + "-meta.json", Json.canonical(Json.obj("httpStatus" -> n(http.status), "requestId" -> opt(http.header("x-request-id")), "durationMs" -> n(clock.millis - begin), "redactionPolicy" -> s("known_secret_values_masked"))))
                        }
                        case _ => ()
                      countTime += clock.millis - begin
                      store.append("events.jsonl", Json.canonical(Json.obj("kind" -> s("token_count_finished"), "trialId" -> s(id), "attempt" -> n(countAttempts), "durationMs" -> n(clock.millis - begin), "time" -> s(Instant.ofEpochMilli(clock.millis).toString))))
                      result match
                        case Right(tokens) =>
                          require(tokens >= 0, "Negative token count")
                          store.atomic(cacheName, Json.canonical(Json.obj("requestHash" -> s(requestHash), "input_tokens" -> n(tokens))))
                        case Left(error) =>
                          store.atomic(s"trials/$id/count-attempt-$countAttempts-error.json", Json.canonical(errorJson(error)))
                          RetryPolicy.decide(error, countAttempts, clock.millis - start, Instant.ofEpochMilli(clock.millis), new java.util.Random(SeedsForRetry.seed("count:" + id, countAttempts)).nextDouble(), retryAmbiguous) match
                            case RetryDecision.Retry(delay) => clock.sleep(delay); retry = true
                            case _ => ()
                  result
              counted match
                case Left(error) if !softBudget =>
                  terminal(store, id, if countAttempts == 0 then "not_dispatched" else outcome(error), "", None, start, clock.millis, countAttempts, 0, "Strict token reservation stopped: " + errorMessage(error), queueWait, countTime)
                  stopped.set(true)
                case _ =>
                  val inputCount = counted.toOption.getOrElse(Math.max(1L, (request.instructions.length + request.input.map(_.content.length).sum).toLong))
                  val reservation = Math.addExact(Math.addExact(inputCount, request.maxOutputTokens), 256L)
                  store.atomic(s"trials/$id/token-count.json", Json.canonical(Json.obj("input_tokens" -> n(inputCount), "exact" -> Bool(counted.isRight && !mock), "synthetic_mock" -> Bool(mock), "soft_budget" -> Bool(counted.isLeft), "requestHash" -> s(requestHash), "durationMs" -> n(countTime))))
                  val firstReservation = s"$id/1"
                  if countAttempts >= 4 || cancelled() then
                    terminal(store, id, "not_dispatched", "", None, start, clock.millis, countAttempts, 0, "Logical attempt/deadline exhausted before generation", queueWait, countTime)
                  else if budget.start(firstReservation, reservation) then
                    var attempt = 0
                    var retry = true
                    var backoff = 0L
                    var lastError = Option.empty[LlmError]
                    while retry && !cancelled() && countAttempts + attempt < 4 do
                      retry = false
                      val nextAttempt = attempt + 1
                      val reservationId = s"$id/$nextAttempt"
                      if nextAttempt > 1 && !budget.reserveRetry(reservationId, reservation) then
                        terminal(store, id, lastError.map(outcome).getOrElse("ambiguous_outcome"), "", None, start, clock.millis, countAttempts, attempt, "Retry budget exhausted; unresolved reservation retained", queueWait, countTime, backoff)
                      else
                        if budget.httpAvailable && throttle.acquire(reservation, () => cancelled()) && !cancelled() && budget.http() then
                          attempt = nextAttempt
                          val begin = clock.millis
                          val response =
                            store.append("usage-ledger.jsonl", Json.canonical(Json.obj("kind" -> s("reserve"), "reservationId" -> s(reservationId), "trialId" -> s(id), "tokens" -> n(reservation), "window" -> s(window))))
                            store.append("events.jsonl", Json.canonical(Json.obj("kind" -> s("dispatch"), "trialId" -> s(id), "attempt" -> n(attempt), "reservationId" -> s(reservationId), "time" -> s(Instant.ofEpochMilli(begin).toString))))
                            client.generate(request)
                          val attemptBase = f"trials/$id/attempt-$attempt%03d"
                          val elapsed = clock.millis - begin
                          response match
                            case Right(r) =>
                              store.atomic(attemptBase + "-body.txt", r.rawBody)
                              store.atomic(attemptBase + "-meta.json", Json.canonical(Json.obj("attempt" -> n(attempt), "httpStatus" -> n(200), "requestId" -> opt(r.requestId), "responseId" -> s(r.responseId), "returnedModel" -> s(r.returnedModel), "status" -> s(r.status), "startedAt" -> s(Instant.ofEpochMilli(begin).toString), "durationMs" -> n(elapsed), "usage" -> usageJson(r.usage), "redactionAppliedToKnownSecrets" -> Bool(true))))
                              r.usage.foreach { u =>
                                budget.settle(reservationId, Some(u.totalTokens))
                                store.append("usage-ledger.jsonl", Json.canonical(Json.obj("kind" -> s("settle"), "reservationId" -> s(reservationId), "trialId" -> s(id), "tokens" -> n(u.totalTokens), "window" -> s(window))))
                                if u.inputTokens > inputCount + 256 || budget.knownTokens + budget.reservedTokens > limits.localTokenCap then
                                  stopped.set(true)
                                  store.append("events.jsonl", Json.canonical(Json.obj("kind" -> s("usage_overrun"), "trialId" -> s(id), "countedInput" -> n(inputCount), "actualInput" -> n(u.inputTokens))))
                              }
                              val scoredStatus = if Set("completed", "incomplete").contains(r.status) then r.status else "api_rejected"
                              terminal(store, id, scoredStatus, r.text, Some(r), start, clock.millis, countAttempts, attempt, if scoredStatus == r.status then "" else "Provider status: " + r.status, queueWait, countTime, backoff)
                            case Left(error) =>
                              lastError = Some(error)
                              val raw = error match
                                case e: ApiError => e.rawBody
                                case e: DecodeError => e.rawBody
                                case _ => ""
                              store.atomic(attemptBase + "-body.txt", raw)
                              store.atomic(attemptBase + "-meta.json", Json.canonical(Json.obj("attempt" -> n(attempt), "startedAt" -> s(Instant.ofEpochMilli(begin).toString), "durationMs" -> n(elapsed), "error" -> errorJson(error), "usage" -> Null)))
                              RetryPolicy.decide(error, attempt, clock.millis - start, Instant.ofEpochMilli(clock.millis), new java.util.Random(SeedsForRetry.seed(id, attempt)).nextDouble(), retryAmbiguous) match
                                case RetryDecision.Retry(delay) if countAttempts + attempt < 4 =>
                                  store.append("events.jsonl", Json.canonical(Json.obj("kind" -> s("retry"), "trialId" -> s(id), "attempt" -> n(attempt), "delayMs" -> n(delay), "reason" -> s(errorMessage(error)))))
                                  clock.sleep(delay); backoff += delay; retry = true
                                case decision => terminal(store, id, outcome(error), "", None, start, clock.millis, countAttempts, attempt, decision.toString + ": " + errorMessage(error), queueWait, countTime, backoff)
                        else
                          budget.cancelUndispatched(reservationId)
                          terminal(store, id, lastError.map(outcome).getOrElse(if attempt > 0 then "ambiguous_outcome" else "not_dispatched"), "", None, start, clock.millis, countAttempts, attempt, "Stopped before next dispatch; earlier unresolved reservations retained", queueWait, countTime, backoff)
                    if !store.exists(s"trials/$id/terminal.json") then
                      if attempt == 0 then budget.cancelUndispatched(firstReservation)
                      terminal(store, id, lastError.map(outcome).getOrElse(if attempt > 0 then "ambiguous_outcome" else "not_dispatched"), "", None,
                        start, clock.millis, countAttempts, attempt, "Logical admission deadline or stop reached before retry dispatch", queueWait, countTime, backoff)
        ) }
        futures.foreach(_.get())
      }
    finally
      stopped.set(true)
      pool.shutdown()
      pool.awaitTermination(180, TimeUnit.SECONDS)
      try Runtime.getRuntime.removeShutdownHook(shutdownHook) catch case _: IllegalStateException => ()
    println(s"Started ${budget.logicalCalls} logical trials; ${budget.httpAttempts} HTTP attempts; known=${budget.knownTokens}, unresolved=${budget.reservedTokens} tokens ($window UTC).")
    RunResult(budget.logicalCalls, budget.httpAttempts, budget.knownTokens, budget.reservedTokens)
  def usageJson(usage: Option[TokenUsage]): JsonValue = usage.map { u =>
    def optional(v: Option[Long]): JsonValue = v.map(n).getOrElse(Null)
    Json.obj("inputTokens" -> n(u.inputTokens), "outputTokens" -> n(u.outputTokens), "totalTokens" -> n(u.totalTokens), "cachedInputTokens" -> optional(u.cachedInputTokens), "cacheWriteTokens" -> optional(u.cacheWriteTokens), "reasoningTokens" -> optional(u.reasoningTokens))
  }.getOrElse(Null)
  def errorMessage(error: LlmError): String = error match
    case e: ApiError => e.message
    case e: TransportError => e.message
    case e: DecodeError => e.message
  def errorJson(error: LlmError): Obj = error match
    case e: ApiError => Json.obj("kind" -> s("api"), "httpStatus" -> n(e.httpStatus), "code" -> opt(e.code), "message" -> s(e.message), "retryAfter" -> opt(e.retryAfter), "requestId" -> opt(e.requestId))
    case e: TransportError => Json.obj("kind" -> s("transport"), "message" -> s(e.message), "ambiguousOutcome" -> Bool(e.ambiguousOutcome))
    case e: DecodeError => Json.obj("kind" -> s("decode"), "message" -> s(e.message))
  def outcome(error: LlmError): String = error match
    case _: ApiError => "api_rejected"
    case e: TransportError => if e.ambiguousOutcome then "ambiguous_outcome" else "transport_failure"
    case _: DecodeError => "decode_failure"
  private def terminal(store: RunStore, id: String, status: String, text: String, response: Option[LlmResponse], start: Long, end: Long, countAttempts: Int, generationAttempts: Int, reason: String, queueWait: Long = 0, countTime: Long = 0, backoff: Long = 0): Unit =
    val parts = response.toVector.flatMap(r => ResponseDecoder.selectedTextParts(r.rawBody).toOption.getOrElse(Vector.empty)).map(part => Json.obj("messageIndex" -> n(part.messageIndex), "partIndex" -> n(part.partIndex), "phase" -> opt(part.phase), "text" -> s(part.text)))
    val artifacts = ((1 to generationAttempts).toVector.flatMap { attempt =>
      Vector(f"trials/$id/attempt-$attempt%03d-body.txt", f"trials/$id/attempt-$attempt%03d-meta.json")
    } ++ (1 to countAttempts).toVector.flatMap { attempt =>
      Vector(f"trials/$id/count-attempt-$attempt%03d-request.json", f"trials/$id/count-attempt-$attempt%03d-body.txt", f"trials/$id/count-attempt-$attempt%03d-meta.json")
    } ++ Vector(s"trials/$id/plan-token-count-request.json", s"trials/$id/plan-token-count-body.txt", s"trials/$id/plan-token-count-meta.json")).filter(store.exists).map(path => Json.obj("path" -> s(path), "sha256" -> s(sha(store.read(path)))))
    store.atomic(s"trials/$id/terminal.json", Json.canonical(Json.obj("id" -> s(id), "status" -> s(status), "text" -> s(text), "refusals" -> Arr(response.toVector.flatMap(_.refusals).map(s)), "incompleteReason" -> opt(response.flatMap(_.incompleteReason)), "responseId" -> opt(response.map(_.responseId)), "returnedModel" -> opt(response.map(_.returnedModel)), "requestId" -> opt(response.flatMap(_.requestId)), "usage" -> usageJson(response.flatMap(_.usage)), "startedAt" -> s(Instant.ofEpochMilli(start).toString), "endedAt" -> s(Instant.ofEpochMilli(end).toString), "durationMs" -> n(end - start), "queueWaitMs" -> n(queueWait), "tokenCountMs" -> n(countTime), "backoffMs" -> n(backoff), "countAttempts" -> n(countAttempts), "generationAttempts" -> n(generationAttempts), "reason" -> s(reason), "selectedTextParts" -> Arr(parts), "artifacts" -> Arr(artifacts))))

private object SeedsForRetry:
  def seed(id: String, attempt: Int): Long = java.nio.ByteBuffer.wrap(java.security.MessageDigest.getInstance("SHA-256").digest(("retry:" + id + ":" + attempt).getBytes(java.nio.charset.StandardCharsets.UTF_8))).getLong
