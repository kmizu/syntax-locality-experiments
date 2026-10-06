package locality.llm

final case class TextMessage(role: String, content: String)
final case class LlmRequest(
  model: String,
  instructions: String,
  input: Vector[TextMessage],
  reasoningEffort: Option[String],
  maxOutputTokens: Int
)
final case class TokenUsage(
  inputTokens: Long,
  outputTokens: Long,
  totalTokens: Long,
  cachedInputTokens: Option[Long],
  cacheWriteTokens: Option[Long],
  reasoningTokens: Option[Long]
)
final case class LlmResponse(
  responseId: String,
  returnedModel: String,
  status: String,
  text: String,
  refusals: Vector[String],
  incompleteReason: Option[String],
  usage: Option[TokenUsage],
  requestId: Option[String],
  rawBody: String
)
sealed trait LlmError
final case class ApiError(
  httpStatus: Int,
  code: Option[String],
  message: String,
  retryAfter: Option[String],
  requestId: Option[String],
  rawBody: String
) extends LlmError
final case class TransportError(message: String, ambiguousOutcome: Boolean) extends LlmError
final case class DecodeError(message: String, rawBody: String) extends LlmError
trait LlmClient:
  def generate(request: LlmRequest): Either[LlmError, LlmResponse]
  def countInputTokens(request: LlmRequest): Either[LlmError, Long]
