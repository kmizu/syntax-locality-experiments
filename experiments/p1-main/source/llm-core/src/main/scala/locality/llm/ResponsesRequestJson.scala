package locality.llm

import locality.llm.json.*

/** Shared request encoding for persistence and the actual Responses HTTP body.
  * Canonical object ordering leaves all strings and input array order unchanged.
  * These bodies contain no authentication headers or configuration secrets.
  */
object ResponsesRequestJson:
  def countInputTokens(request: LlmRequest): Obj = Json.obj(
    "model" -> Str(request.model),
    "instructions" -> Str(request.instructions),
    "input" -> Arr(request.input.map(m => Json.obj("role" -> Str(m.role), "content" -> Str(m.content))))
  )

  def generation(request: LlmRequest): Obj = Obj(
    countInputTokens(request).fields ++ Vector(
      "max_output_tokens" -> Num(BigDecimal(request.maxOutputTokens)),
      "store" -> Bool(false), "stream" -> Bool(false), "truncation" -> Str("disabled")
    ) ++ request.reasoningEffort.map(e => "reasoning" -> Json.obj("effort" -> Str(e)))
  )

  def countInputTokensBody(request: LlmRequest): String = Json.canonical(countInputTokens(request))
  def generationBody(request: LlmRequest): String = Json.canonical(generation(request))
