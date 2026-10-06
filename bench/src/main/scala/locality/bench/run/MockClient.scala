package locality.bench.run

import locality.llm.*
import locality.llm.json.*
import JsonSupport.*

/** Wiring-only fixture. Only this explicit mock may see gold answers. */
final class MockClient(store: RunStore) extends LlmClient:
  private val gold = store.jsonLines("gold.jsonl").map(g => str(g, "id") -> g).toMap
  private val answers = store.jsonLines("cases.jsonl").map { c =>
    val g = gold(str(c, "id"))
    val text = str(c, "task") match
      case "scope_lookup" => num(g, "value").toString
      case "active_stack" => Json.render(field(g, "stack"))
      case "ast_to_source" => str(g, "source")
      case "source_to_ast" => Json.render(field(g, "ast"))
      case other => throw new IllegalArgumentException(s"Unknown mock task: $other")
    str(c, "requestHash") -> text
  }.toMap
  def countInputTokens(request: LlmRequest): Either[LlmError, Long] = Right(Math.max(1L, Json.canonical(requestJson(request)).length / 4L))
  def generate(request: LlmRequest): Either[LlmError, LlmResponse] =
    val key = hash(requestJson(request))
    val text = answers(key)
    val input = countInputTokens(request).toOption.get
    val output = Math.max(1L, text.length / 4L)
    val raw = Json.canonical(Json.obj("id" -> s("synthetic-mock-" + key.take(12)), "model" -> s(request.model), "status" -> s("completed"),
      "output" -> Arr(Vector(Json.obj("type" -> s("message"), "role" -> s("assistant"), "content" -> Arr(Vector(Json.obj("type" -> s("output_text"), "text" -> s(text))))))),
      "usage" -> Json.obj("input_tokens" -> n(input), "output_tokens" -> n(output), "total_tokens" -> n(input + output))))
    ResponseDecoder.decode(raw)
