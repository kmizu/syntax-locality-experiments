package locality.bench.run

import locality.llm.*
import locality.llm.json.*
import JsonSupport.*
import java.nio.file.Path
import java.time.{Instant, LocalDate, ZoneOffset}

object Preflight:
  def run(out: Path, model: String, client: LlmClient, limits: Limits): Unit =
    require(limits.maxHttpAttempts >= 2, "Preflight requires count plus generation HTTP allowance")
    require(limits.localTokenCap >= 8192L + 256L, "Token cap cannot cover minimum preflight reservation")
    val store = new RunStore(out)
    try
      require(!Vector("request.json", "count-attempt-001-request.json", "capabilities.json", "events.jsonl", "usage-ledger.jsonl").exists(store.exists), "Preflight output was initialized; use a fresh directory and account for prior usage/reservations")
      val window = LocalDate.now(ZoneOffset.UTC).toString
      val request = LlmRequest(model, "Return only the requested decimal integer.", Vector(TextMessage("user", "Return only 5916.")), Some("low"), 8192)
      store.atomic("request.json", ResponsesRequestJson.generationBody(request))
      val budget = new Budget(limits.copy(maxGenerationCalls = 1), 0, Vector.empty)
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
      val countMs = System.currentTimeMillis() - countStart
      counted match
        case Left(error) =>
          store.atomic("capabilities.json", Json.canonical(Json.obj("model" -> s(model), "reasoningEffort" -> s("low"), "tokenCountSupported" -> Bool(false), "generationSupported" -> Bool(false), "verified" -> Bool(false), "synthetic_mock" -> Bool(false), "error" -> Runner.errorJson(error), "checkedAt" -> s(Instant.now.toString))))
          throw new IllegalStateException("Token count preflight failed: " + Runner.errorMessage(error))
        case Right(tokens) =>
          store.atomic("token-count.json", Json.canonical(Json.obj("input_tokens" -> n(tokens), "durationMs" -> n(countMs))))
          require(tokens >= 0, "Negative token count")
          val reservation = Math.addExact(Math.addExact(tokens, request.maxOutputTokens), 256L)
          require(budget.start("preflight/1", reservation), "Token cap insufficient for preflight reservation")
          require(budget.http(), "HTTP limit before generation")
          require(LocalDate.now(ZoneOffset.UTC).toString == window, "UTC window changed before generation; use a fresh preflight directory")
          store.append("usage-ledger.jsonl", Json.canonical(Json.obj("kind" -> s("reserve"), "reservationId" -> s("preflight/1"), "tokens" -> n(reservation), "window" -> s(window), "time" -> s(Instant.now.toString))))
          store.append("events.jsonl", Json.canonical(Json.obj("kind" -> s("dispatch"), "time" -> s(Instant.now.toString))))
          val begin = System.currentTimeMillis()
          client.generate(request) match
            case Left(error) =>
              store.atomic("attempt-001-meta.json", Json.canonical(Runner.errorJson(error)))
              error match
                case e: ApiError => store.atomic("attempt-001-body.txt", e.rawBody)
                case e: DecodeError => store.atomic("attempt-001-body.txt", e.rawBody)
                case _ => ()
              store.atomic("capabilities.json", Json.canonical(Json.obj("model" -> s(model), "reasoningEffort" -> s("low"), "tokenCountSupported" -> Bool(true), "generationSupported" -> Bool(false), "verified" -> Bool(false), "synthetic_mock" -> Bool(false), "error" -> Runner.errorJson(error), "unresolvedReservation" -> n(reservation), "checkedAt" -> s(Instant.now.toString))))
              throw new IllegalStateException("Generation preflight failed: " + Runner.errorMessage(error))
            case Right(response) =>
              store.atomic("attempt-001-body.txt", response.rawBody)
              store.atomic("attempt-001-meta.json", Json.canonical(Json.obj("responseId" -> s(response.responseId), "requestId" -> opt(response.requestId), "returnedModel" -> s(response.returnedModel), "status" -> s(response.status), "usage" -> Runner.usageJson(response.usage), "durationMs" -> n(System.currentTimeMillis() - begin))))
              response.usage.foreach { u => budget.settle("preflight/1", Some(u.totalTokens)); store.append("usage-ledger.jsonl", Json.canonical(Json.obj("kind" -> s("settle"), "reservationId" -> s("preflight/1"), "tokens" -> n(u.totalTokens), "window" -> s(window)))) }
              val passed = response.status == "completed" && response.refusals.isEmpty && response.usage.nonEmpty && response.usage.forall(_.inputTokens <= tokens + 256) && budget.knownTokens <= limits.localTokenCap
              store.atomic("capabilities.json", Json.canonical(Json.obj("model" -> s(model), "reasoningEffort" -> s("low"), "maxOutputTokensReading" -> n(8192), "endpoint" -> s("https://api.openai.com/v1"), "tokenCountSupported" -> Bool(true), "generationSupported" -> Bool(passed), "verified" -> Bool(passed), "synthetic_mock" -> Bool(false), "generationResponseId" -> s(response.responseId), "returnedModel" -> s(response.returnedModel), "checkedAt" -> s(Instant.now.toString), "knownTokens" -> n(budget.knownTokens), "unresolvedTokens" -> n(budget.reservedTokens), "httpAttempts" -> n(budget.httpAttempts))))
              require(passed, "Preflight response incomplete/refusal/usage unavailable or token drift; no automatic fallback")
              println(s"Preflight passed for $model: ${budget.httpAttempts} HTTP attempts, ${budget.knownTokens} known tokens.")
    finally store.close()
