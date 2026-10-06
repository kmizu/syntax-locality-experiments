package locality.bench.score

enum Outcome(val id: String, val evaluable: Boolean):
  case Correct extends Outcome("correct", true)
  case WrongAnswer extends Outcome("wrong_answer", true)
  case InvalidAnswerFormat extends Outcome("invalid_answer_format", true)
  case InvalidGeneratedSyntax extends Outcome("invalid_generated_syntax", true)
  case ValidSyntaxWrongAst extends Outcome("valid_syntax_wrong_ast", true)
  case Refusal extends Outcome("refusal", true)
  case IncompleteOutput extends Outcome("incomplete_output", true)
  case ApiRejected extends Outcome("api_rejected", false)
  case TransportFailure extends Outcome("transport_failure", false)
  case AmbiguousOutcome extends Outcome("ambiguous_outcome", false)
  case DecodeFailure extends Outcome("decode_failure", false)
  case NotDispatched extends Outcome("not_dispatched", false)

final case class GradingResponse(status: String, text: String, refusal: Boolean = false)

final case class Score(
    outcome: Outcome,
    strictCorrect: Boolean,
    auxiliaryCorrect: Option[Boolean] = None,
    reason: String = "",
    diagnostics: Map[String, String] = Map.empty,
    syntaxValid: Option[Boolean] = None
):
  def evaluable: Boolean = outcome.evaluable
  def infrastructureMissing: Boolean = !evaluable && outcome != Outcome.NotDispatched

object Scoring:
  def status(response: GradingResponse): Option[Outcome] =
    if response.refusal then Some(Outcome.Refusal)
    else response.status match
      case "completed" => None
      case "refusal" => Some(Outcome.Refusal)
      case "incomplete" | "incomplete_output" => Some(Outcome.IncompleteOutput)
      case "api_rejected" => Some(Outcome.ApiRejected)
      case "transport_failure" => Some(Outcome.TransportFailure)
      case "ambiguous_outcome" => Some(Outcome.AmbiguousOutcome)
      case "decode_failure" => Some(Outcome.DecodeFailure)
      case "not_dispatched" => Some(Outcome.NotDispatched)
      case other => throw new IllegalArgumentException(s"Unknown response status: $other")

  /** Auxiliary normalization is deliberately limited to one whole-answer fence. */
  def removeWholeFence(text: String): Option[String] =
    val lines = text.trim.replace("\r\n", "\n").split("\n", -1).toVector
    if lines.size >= 3 && lines.head.matches("```[A-Za-z0-9_+-]*") && lines.last == "```" &&
        !lines.slice(1, lines.size - 1).exists(_.trim.startsWith("```")) then
      Some(lines.slice(1, lines.size - 1).mkString("\n"))
    else None

  def applyStatus(response: GradingResponse, bodyScore: => Score): Score =
    status(response) match
      case None => bodyScore
      case Some(Outcome.IncompleteOutput) =>
        Score(Outcome.IncompleteOutput, false, Some(bodyScore.strictCorrect), "Response was incomplete")
      case Some(other) => Score(other, false, reason = s"Response status: ${other.id}")
