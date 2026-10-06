package locality.llm

import java.time.Instant
import java.time.{Duration, ZonedDateTime}
import java.time.format.DateTimeFormatter
import scala.util.Try

enum RetryDecision:
  case Stop(reason: String)
  case Retry(delayMillis: Long)

object RetryPolicy:
  def decide(error: LlmError, attempt: Int, elapsedMillis: Long, now: Instant, jitterFraction: Double, retryAmbiguous: Boolean = false): RetryDecision =
    require(attempt >= 1 && elapsedMillis >= 0, "Attempt and elapsed time must be valid")
    require(jitterFraction >= 0 && jitterFraction < 1 && !jitterFraction.isNaN, "Jitter fraction must lie in [0,1)")
    if attempt >= 4 then return RetryDecision.Stop("Maximum four HTTP attempts reached")
    if elapsedMillis >= 600000 then return RetryDecision.Stop("Maximum 600 second elapsed time reached")
    val retryable = error match
      case ApiError(status,code,message,_,_,_) =>
        val c = code.getOrElse("").toLowerCase(java.util.Locale.ROOT)
        val correctionNeeded = c.contains("quota") || c.contains("billing") || c.contains("payment") || c.contains("credit") || c == "usage_limit_reached"
        !correctionNeeded && ((status == 429 && (c.startsWith("rate_limit") || message.toLowerCase(java.util.Locale.ROOT).contains("rate limit"))) || status >= 500 && status <= 599)
      case TransportError(_,ambiguous) => ambiguous && retryAmbiguous
      case _: DecodeError => false
    if !retryable then return RetryDecision.Stop("Error is not retryable")
    val header = error match
      case e: ApiError => e.retryAfter
      case _ => None
    val serverDelay: Option[Long] = header.flatMap { value =>
      val trimmed = value.trim
      if trimmed.nonEmpty && trimmed.forall(c => c >= '0' && c <= '9') then
        val seconds = BigInt(trimmed)
        Some(if seconds > 600 then Long.MaxValue else seconds.toLong * 1000)
      else
        Try(ZonedDateTime.parse(trimmed,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant).toOption.map { date =>
          if !date.isAfter(now) then 0L
          else Try(Duration.between(now,date).toMillis).getOrElse(Long.MaxValue)
        }
    }
    val jitterCeiling = math.min(60000L,1000L * (1L << math.min(attempt - 1,16)))
    val delay = serverDelay.getOrElse((jitterFraction * jitterCeiling).toLong)
    if delay > 600000L - elapsedMillis then RetryDecision.Stop("Retry-After or backoff exceeds elapsed-time budget")
    else RetryDecision.Retry(delay)
