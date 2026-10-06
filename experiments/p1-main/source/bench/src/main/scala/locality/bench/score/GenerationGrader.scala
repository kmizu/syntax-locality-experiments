package locality.bench.score

import locality.bench.lang.*

object GenerationGrader:
  def grade(gold: Program, response: GradingResponse, spec: RenderSpec): Score =
    val score = Scoring.applyStatus(response, gradeBody(gold, response.text, spec))
    // Strict generation validity requires a completed parseable response. Refusal and
    // incomplete output remain evaluable failures rather than vanishing from its denominator.
    if score.evaluable && score.syntaxValid.isEmpty then score.copy(syntaxValid = Some(false)) else score

  private def gradeBody(gold: Program, text: String, spec: RenderSpec): Score =
    val auxiliary = Scoring.removeWholeFence(text).map(t => gradeBody(gold, t, spec).strictCorrect)
    Parser.parseProgram(text, spec) match
      case Right(actual) if actual == gold => Score(Outcome.Correct, true, auxiliary, syntaxValid = Some(true))
      case Right(_) => Score(Outcome.ValidSyntaxWrongAst, false, auxiliary, "Parsed AST differs from the original", syntaxValid = Some(true))
      case Left(error) =>
        Score(Outcome.InvalidGeneratedSyntax, false, auxiliary, error.message,
          Map("parse_error_code" -> error.code, "parse_error_line" -> error.line.toString,
            "parse_error_column" -> error.column.toString), Some(false))
