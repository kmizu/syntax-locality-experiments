package locality.bench.score

object LookupGrader:
  def grade(gold: Int, response: GradingResponse): Score =
    Scoring.applyStatus(response, gradeBody(gold, response.text))

  private def gradeBody(gold: Int, text: String): Score =
    val trimmed = text.trim
    val value = if trimmed.matches("-?[0-9]+") then Some(BigInt(trimmed)) else None
    val correct = value.contains(BigInt(gold))
    val auxiliary = Scoring.removeWholeFence(text).map(t => gradeBody(gold, t).strictCorrect)
    value match
      case None => Score(Outcome.InvalidAnswerFormat, false, auxiliary, "Expected one whole decimal integer")
      case Some(_) if correct => Score(Outcome.Correct, true, auxiliary)
      case Some(actual) => Score(Outcome.WrongAnswer, false, auxiliary, s"Expected $gold; received $actual")
