package locality.llm

import locality.llm.json.*
import java.time.Instant

object ResponseTests:
  val completed = """{"id":"resp_mock","model":"gpt-5.6-terra","status":"completed","output":[{"type":"reasoning","summary":[{"text":"not an answer"}]},{"type":"message","role":"assistant","content":[{"type":"output_text","text":"59"},{"type":"output_text","text":"16"}]}],"usage":{"input_tokens":100,"output_tokens":20,"total_tokens":120,"input_tokens_details":{"cached_tokens":30,"cache_write_tokens":10},"output_tokens_details":{"reasoning_tokens":15}},"unknown_future_field":true}"""
  private def response(text: String): LlmResponse = ResponseDecoder.decode(text).fold(e => throw new AssertionError(e), identity)
  def register(): Unit =
    TestHarness.test("responses", "Reasoning does not become answer and all text parts concatenate") {
      val result = response(completed)
      assert(result.text == "5916")
      assert(result.usage.contains(TokenUsage(100,20,120,Some(30),Some(10),Some(15))))
      assert(result.rawBody == completed)
      assert(ResponseDecoder.decode(completed,Some("req_a")).toOption.get.requestId.contains("req_a"))
    }
    TestHarness.test("responses", "Explicit final phase excludes commentary, reasoning and user messages") {
      val raw = """{"id":"r","model":"m","status":"completed","output":[{"type":"message","role":"assistant","phase":"commentary","content":[{"type":"output_text","text":"Thinking"}]},{"type":"message","role":"user","content":[{"type":"output_text","text":"Do not copy"}]},{"type":"message","role":"assistant","phase":"final_answer","content":[{"type":"output_text","text":"5916"}]}]}"""
      assert(response(raw).text == "5916")
    }
    TestHarness.test("responses", "Refusal and incomplete preserve text, status, usage, and unknown cache fields") {
      val raw = """{"id":"r","model":"m","status":"incomplete","output":[{"type":"message","role":"assistant","content":[{"type":"output_text","text":"59"},{"type":"refusal","refusal":"refused"}]}],"incomplete_details":{"reason":"max_output_tokens"},"usage":{"input_tokens":1,"output_tokens":2,"total_tokens":3}}"""
      val result = response(raw)
      assert(result.status == "incomplete" && result.text == "59")
      assert(result.refusals == Vector("refused"))
      assert(result.incompleteReason.contains("max_output_tokens"))
      assert(result.usage.get.reasoningTokens.isEmpty && result.usage.get.cachedInputTokens.isEmpty)
      assert(response("""{"id":"r","model":"m","status":"completed","output":[],"usage":null}""").usage.isEmpty)
    }
    TestHarness.test("responses", "Invalid JSON and wrong required field types are decode failures") {
      Vector("not json", "{}", completed.replace("\"resp_mock\"", "42"), completed.replace("\"text\":\"59\"", "\"text\":42"), completed.replace("\"input_tokens\":100", "\"input_tokens\":1.5"), completed.replace("\"input_tokens\":100", "\"input_tokens\":9223372036854775808"), completed.replace("\"cached_tokens\":30", "\"cached_tokens\":-1")).foreach { raw =>
        assert(ResponseDecoder.decode(raw).isLeft, raw)
      }
    }
    TestHarness.test("responses", "Part boundaries and selected message indices remain available for logs") {
      val parts = ResponseDecoder.selectedTextParts(completed).toOption.get
      assert(parts.map(p => (p.messageIndex,p.partIndex,p.text)) == Vector((1,0,"59"),(1,1,"16")))
      assert(parts.forall(_.phase.isEmpty))
    }
    TestHarness.test("responses", "Retry pure policy distinguishes rate limits, quota, auth and bad parameters") {
      val now = Instant.parse("2026-10-05T10:00:00Z")
      def decision(status: Int, code: String): RetryDecision = RetryPolicy.decide(ApiError(status,Some(code),"error",None,None,""),1,0,now,0.5)
      assert(decision(429,"rate_limit_exceeded") == RetryDecision.Retry(500))
      Vector(400,401,403,404).foreach(s => assert(decision(s,"invalid_request_error").isInstanceOf[RetryDecision.Stop]))
      assert(decision(429,"insufficient_quota").isInstanceOf[RetryDecision.Stop])
      assert(decision(429,"billing_hard_limit_reached").isInstanceOf[RetryDecision.Stop])
      assert(decision(429,"unknown_failure").isInstanceOf[RetryDecision.Stop])
      assert(decision(503,"server_error") == RetryDecision.Retry(500))
      assert(RetryPolicy.decide(TransportError("reset",true),1,0,now,0.5).isInstanceOf[RetryDecision.Stop])
      assert(RetryPolicy.decide(TransportError("reset",true),1,0,now,0.5,true) == RetryDecision.Retry(500))
      assert(RetryPolicy.decide(TransportError("invalid endpoint",false),1,0,now,0.5).isInstanceOf[RetryDecision.Stop])
      assert(RetryPolicy.decide(DecodeError("bad json",""),1,0,now,0.5).isInstanceOf[RetryDecision.Stop])
    }
    TestHarness.test("responses", "Retry-After respects seconds, HTTP date, invalid jitter fallback, attempt and elapsed cap") {
      val now = Instant.parse("2026-10-05T10:00:00Z")
      def decide(header: String, attempt: Int = 1, elapsed: Long = 0) =
        RetryPolicy.decide(ApiError(429,Some("rate_limit_exceeded"),"rate",Some(header),None,""),attempt,elapsed,now,0.25)
      assert(decide("2") == RetryDecision.Retry(2000))
      assert(decide("Mon, 05 Oct 2026 10:00:05 GMT") == RetryDecision.Retry(5000))
      assert(decide("nonsense", 3) == RetryDecision.Retry(1000))
      assert(decide("999").isInstanceOf[RetryDecision.Stop])
      assert(decide("5",1,598000).isInstanceOf[RetryDecision.Stop])
      assert(decide("1",4).isInstanceOf[RetryDecision.Stop])
    }
