package locality.bench.score

import locality.bench.lang.*
import locality.bench.prompt.AstJson

object ParsingGrader:
  def grade(gold: Program, response: GradingResponse): Score =
    val score = Scoring.applyStatus(response, gradeBody(gold, response.text))
    if score.evaluable && score.syntaxValid.isEmpty then score.copy(syntaxValid = Some(false)) else score

  private def gradeBody(gold: Program, text: String): Score =
    val auxiliary = Scoring.removeWholeFence(text).map(t => gradeBody(gold, t).strictCorrect)
    AstJson.decode(text) match
      case Right(actual) if actual == gold => Score(Outcome.Correct, true, auxiliary, syntaxValid = Some(true))
      case Right(actual) =>
        val mismatch = bodyMismatch(gold.body, actual.body, "$.body").get
        Score(Outcome.ValidSyntaxWrongAst, false, auxiliary, "AST differs from the original",
          Map("ast_mismatch_class" -> mismatch.kind, "ast_mismatch_path" -> mismatch.path), Some(true))
      case Left(error) =>
        Score(Outcome.InvalidAnswerFormat, false, auxiliary, error.message,
          Map("ast_error_code" -> error.code, "ast_error_path" -> error.path), Some(false))

  private final case class Mismatch(kind: String, path: String)

  private def bodyMismatch(gold: Vector[Stmt], actual: Vector[Stmt], path: String): Option[Mismatch] =
    if gold == actual then None
    else if gold.groupMapReduce(identity)(_ => 1)(_ + _) == actual.groupMapReduce(identity)(_ => 1)(_ + _) then
      Some(Mismatch("order", path))
    else
      gold.zip(actual).zipWithIndex.iterator.map { case ((expected, received), index) =>
        nodeMismatch(expected, received, s"$path[$index]")
      }.collectFirst { case Some(mismatch) => mismatch }.orElse {
        if gold.size > actual.size then Some(Mismatch("missing_child", s"$path[${actual.size}]"))
        else if gold.size < actual.size then Some(Mismatch("extra_child", s"$path[${gold.size}]"))
        else None
      }

  private def nodeMismatch(gold: Stmt, actual: Stmt, path: String): Option[Mismatch] =
    (gold, actual) match
      case (Stmt.Scope(goldKind, goldName, goldBody), Stmt.Scope(actualKind, actualName, actualBody)) =>
        if goldKind != actualKind then Some(Mismatch("kind", s"$path.kind"))
        else if goldName != actualName then Some(Mismatch("name", s"$path.name"))
        else bodyMismatch(goldBody, actualBody, s"$path.body")
      case (Stmt.Let(goldVariable, goldValue), Stmt.Let(actualVariable, actualValue)) =>
        if goldVariable != actualVariable then Some(Mismatch("variable", s"$path.variable"))
        else if goldValue != actualValue then Some(Mismatch("value", s"$path.value"))
        else None
      case (Stmt.Nop(goldPayload), Stmt.Nop(actualPayload)) =>
        if goldPayload != actualPayload then Some(Mismatch("payload", s"$path.payload")) else None
      case _ => Some(Mismatch("node_type", s"$path.type"))
