package locality.bench

import locality.bench.score.*
import locality.bench.lang.*

object ScoreTests:
  def run(): Unit =
    assert(LookupGrader.grade(5916, GradingResponse("completed", " 5916\n")).strictCorrect,
      "a whole decimal integer must be accepted")
    assert(LookupGrader.grade(5916, GradingResponse("completed", "answer: 5916")).outcome == Outcome.InvalidAnswerFormat)
    assert(LookupGrader.grade(5916, GradingResponse("completed", "8604")).outcome == Outcome.WrongAnswer)
    assert(!LookupGrader.grade(5916, GradingResponse("incomplete", "5916")).strictCorrect)
    assert(LookupGrader.grade(5916, GradingResponse("incomplete", "5916")).auxiliaryCorrect.contains(true))
    assert(LookupGrader.grade(5916, GradingResponse("completed", "```text\n5916\n```")).auxiliaryCorrect.contains(true))
    assert(!LookupGrader.grade(5916, GradingResponse("completed", "```text\n5916\n```")).strictCorrect)
    assert(LookupGrader.grade(5916, GradingResponse("refusal", "")).evaluable)
    assert(LookupGrader.grade(5916, GradingResponse("api_rejected", "")).infrastructureMissing)
    assert(!LookupGrader.grade(5916, GradingResponse("not_dispatched", "")).infrastructureMissing)
    assert(LookupGrader.grade(5916, GradingResponse("completed", "5916.0")).outcome == Outcome.InvalidAnswerFormat)
    assert(LookupGrader.grade(5916, GradingResponse("completed", "5916 8604")).outcome == Outcome.InvalidAnswerFormat)
    val names = Vector("n_abcdefgh", "n_ijklmnop")
    assert(StackGrader.grade(names, GradingResponse("completed", "[\"n_abcdefgh\",\"n_ijklmnop\"]")).strictCorrect)
    assert(StackGrader.grade(Vector.empty, GradingResponse("completed", "[]")).strictCorrect)
    assert(StackGrader.grade(names, GradingResponse("completed", "[\"n_ijklmnop\",\"n_abcdefgh\"]")).diagnostics.get("order_mismatch").contains("true"))
    assert(StackGrader.grade(names, GradingResponse("completed", "[1,2]")).outcome == Outcome.InvalidAnswerFormat)
    assert(StackGrader.grade(names, GradingResponse("completed", "[\"n_closedxx\"]"), names.toSet + "n_closedxx").diagnostics.get("closed_names").contains("n_closedxx"))
    val program = Program(Vector(Stmt.Let("x_a", 5916), Stmt.Scope(Kind.FuncScope, "n_abcdefgh", Vector(Stmt.Nop("p_abcdefgh")))))
    SyntaxStyle.values.foreach { style =>
      val spec = RenderSpec(style, Lexicon.natural)
      val source = Renderer.renderProgram(program, spec)
      assert(GenerationGrader.grade(program, GradingResponse("completed", source), spec).strictCorrect)
      assert(GenerationGrader.grade(program, GradingResponse("completed", source.replace("5916", "8604")), spec).outcome == Outcome.ValidSyntaxWrongAst)
    }
    val spec = RenderSpec(SyntaxStyle.NamedEnd, Lexicon.natural)
    val mismatch = "func n_abcdefgh\nend func n_ijklmnop\n"
    val invalid = GenerationGrader.grade(Program(Vector(Stmt.Scope(Kind.FuncScope,"n_abcdefgh",Vector.empty))), GradingResponse("completed", mismatch), spec)
    assert(invalid.outcome == Outcome.InvalidGeneratedSyntax && invalid.diagnostics.contains("parse_error_code"))
    val validSource = Renderer.renderProgram(program, spec)
    assert(GenerationGrader.grade(program, GradingResponse("incomplete", validSource), spec).syntaxValid.contains(false),
      "incomplete generation must not disappear from the strict syntax denominator")
    assert(GenerationGrader.grade(program, GradingResponse("refusal", ""), spec).syntaxValid.contains(false))
    assert(GenerationGrader.grade(program, GradingResponse("transport_failure", ""), spec).syntaxValid.isEmpty)
    val ordered = Program(Vector(Stmt.Scope(Kind.FuncScope, "n_abcdefgh", Vector(Stmt.Let("x_a", 1234), Stmt.Nop("p_abcdefgh")))))
    val reordered = Program(Vector(Stmt.Scope(Kind.FuncScope, "n_abcdefgh", Vector(Stmt.Nop("p_abcdefgh"), Stmt.Let("x_a", 1234)))))
    for lexicon <- Lexicon.natural +: Lexicon.fixedNonce; style <- SyntaxStyle.values do
      val orderSpec = RenderSpec(style, lexicon)
      val orderScore = GenerationGrader.grade(ordered, GradingResponse("completed", Renderer.renderProgram(reordered, orderSpec)), orderSpec)
      assert(orderScore.outcome == Outcome.ValidSyntaxWrongAst && !orderScore.strictCorrect && orderScore.syntaxValid.contains(true), "Statement order is part of the exact AST grade")
    CloseDiagnosticsTests.grading()
