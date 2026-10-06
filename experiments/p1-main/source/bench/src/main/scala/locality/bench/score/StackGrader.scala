package locality.bench.score

import locality.llm.json.*

object StackGrader:
  def grade(gold: Vector[String], response: GradingResponse, knownNames: Set[String] = Set.empty): Score =
    Scoring.applyStatus(response, gradeBody(gold, response.text, knownNames))

  private def gradeBody(gold: Vector[String], text: String, knownNames: Set[String]): Score =
    val auxiliary = Scoring.removeWholeFence(text).map(t => gradeBody(gold, t, knownNames).strictCorrect)
    Json.parse(text) match
      case Right(Arr(values)) if values.forall(_.isInstanceOf[Str]) =>
        val actual = values.collect { case Str(value) => value }
        val known = if knownNames.isEmpty then gold.toSet else knownNames
        val diagnostics = Map(
          "element_count_difference" -> (actual.size - gold.size).toString,
          "unknown_names" -> actual.filterNot(known).distinct.mkString(","),
          "closed_names" -> actual.filter(n => known(n) && !gold.contains(n)).distinct.mkString(","),
          "order_mismatch" -> (actual != gold && actual.sorted == gold.sorted).toString
        )
        if actual == gold then Score(Outcome.Correct, true, auxiliary, diagnostics = diagnostics)
        else Score(Outcome.WrongAnswer, false, auxiliary, "Active scope names or order differ", diagnostics)
      case Right(_) => Score(Outcome.InvalidAnswerFormat, false, auxiliary, "Expected a JSON array containing only strings")
      case Left(error) => Score(Outcome.InvalidAnswerFormat, false, auxiliary, s"Invalid whole-answer JSON at ${error.offset}: ${error.reason}")
