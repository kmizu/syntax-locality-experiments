package locality.bench

import locality.bench.lang.*
import locality.bench.score.*

object CloseDiagnosticsTests:
  private val allLexicons = Lexicon.natural +: Lexicon.fixedNonce
  private val gold = Program(Vector(Stmt.Scope(Kind.FuncScope, "n_abcdefgh", Vector(Stmt.Let("x_a", 1234)))))

  private def source(lexicon: Lexicon, ending: String): String =
    s"${lexicon.func} n_abcdefgh\n${lexicon.let} x_a 1234\n$ending\n"

  private def checkError(text: String, spec: RenderSpec, code: String, line: Int = 3): Unit =
    // Both parser modes must reject the same first bad closing line.
    for parsed <- Vector(Parser.parseProgram(text, spec), Parser.parsePrefix(text + "probe q_abcdefgh x_a\n", spec)) do
      assert(parsed.isLeft, s"Malformed close accepted: ${spec.style}/${spec.lexicon.regime}: $text")
      val error = parsed.swap.toOption.get
      assert(error.code == code, s"${spec.style}/${spec.lexicon.regime}: expected $code; received ${error.code}")
      assert(error.line == line && error.column == 1, s"First error location changed: $error")

  def kindMismatch(lexicons: Vector[Lexicon] = allLexicons): Unit =
    for lexicon <- lexicons; style <- Vector(SyntaxStyle.TypedEnd, SyntaxStyle.NamedEnd) do
      val ending = s"${lexicon.end} ${lexicon.area}" + (if style == SyntaxStyle.NamedEnd then " n_abcdefgh" else "")
      checkError(source(lexicon, ending), RenderSpec(style, lexicon), "close_kind_mismatch")

  def nameMismatch(lexicons: Vector[Lexicon] = allLexicons): Unit =
    lexicons.foreach { lexicon =>
      checkError(source(lexicon, s"${lexicon.end} ${lexicon.func} n_ijklmnop"), RenderSpec(SyntaxStyle.NamedEnd, lexicon), "close_name_mismatch")
    }

  def precedence(lexicons: Vector[Lexicon] = allLexicons): Unit =
    lexicons.foreach { lexicon =>
      val spec = RenderSpec(SyntaxStyle.NamedEnd, lexicon)
      checkError(source(lexicon, s"${lexicon.end} ${lexicon.area} n_ijklmnop"), spec, "close_kind_mismatch")
      val start = s"${lexicon.unit} n_abcdefgh\n${lexicon.func} n_ijklmnop\n${lexicon.let} x_a 1234\n"
      checkError(start + s"${lexicon.end} ${lexicon.func} n_qrstuvwx\n${lexicon.end} ${lexicon.area} n_abcdefgh\n", spec, "close_name_mismatch", 4)
      checkError(start + s"${lexicon.end} ${lexicon.area} n_ijklmnop\n${lexicon.end} ${lexicon.unit} n_qrstuvwx\n", spec, "close_kind_mismatch", 4)
    }

  def malformed(lexicons: Vector[Lexicon] = allLexicons): Unit =
    lexicons.foreach { lexicon =>
      val typed = RenderSpec(SyntaxStyle.TypedEnd, lexicon)
      val named = RenderSpec(SyntaxStyle.NamedEnd, lexicon)
      for ending <- Vector(lexicon.end, s"${lexicon.end} ${lexicon.area} extra", s"${lexicon.end} unknownkind", "}") do
        checkError(source(lexicon, ending), typed, "close_mismatch")
      for ending <- Vector(s"${lexicon.end} ${lexicon.area}", s"${lexicon.end} ${lexicon.func} n_bad", s"${lexicon.end} ${lexicon.area} n_ijklmnop extra", s"${lexicon.end} unknownkind n_abcdefgh", s"} ${lexicon.func} n_abcdefgh") do
        checkError(source(lexicon, ending), named, "close_mismatch")
      checkError(source(lexicon, s"${lexicon.end} junk z_baaaaaaa"), RenderSpec(SyntaxStyle.PaddedEnd, lexicon), "close_mismatch")
      checkError(source(lexicon, s"${lexicon.end} ${lexicon.func}"), RenderSpec(SyntaxStyle.GenericEnd, lexicon), "close_mismatch")
      val bracesSource = s"${lexicon.func} n_abcdefgh {\n${lexicon.let} x_a 1234\n${lexicon.end}\n"
      checkError(bracesSource, RenderSpec(SyntaxStyle.Braces, lexicon), "close_mismatch")
      checkError(s"${lexicon.end} ${lexicon.area} n_ijklmnop\n", named, "underflow", 1)
      val unclosed = s"${lexicon.func} n_abcdefgh\n"
      val eof = Parser.parseProgram(unclosed, named).swap.toOption.get
      assert(eof.code == "unclosed_scope" && eof.line == 2 && eof.column == 1)
      assert(Parser.parsePrefix(unclosed + "probe q_abcdefgh x_a\n", named).isRight, "Open scopes are intentional in a reading prefix")
      checkError(source(lexicon, s"${lexicon.end} ${lexicon.func} n_abcdefgh") + "unsupported\n", named, "unknown_statement", 4)
    }

  def grading(lexicons: Vector[Lexicon] = allLexicons): Unit =
    lexicons.foreach { lexicon =>
      val fixtures = Vector(
        (SyntaxStyle.TypedEnd, s"${lexicon.end} ${lexicon.area}", "close_kind_mismatch"),
        (SyntaxStyle.NamedEnd, s"${lexicon.end} ${lexicon.area} n_abcdefgh", "close_kind_mismatch"),
        (SyntaxStyle.NamedEnd, s"${lexicon.end} ${lexicon.func} n_ijklmnop", "close_name_mismatch"),
        (SyntaxStyle.NamedEnd, s"${lexicon.end} ${lexicon.area} n_ijklmnop", "close_kind_mismatch")
      )
      fixtures.foreach { (style, ending, code) =>
        val score = GenerationGrader.grade(gold, GradingResponse("completed", source(lexicon, ending)), RenderSpec(style, lexicon))
        assert(score.outcome == Outcome.InvalidGeneratedSyntax && !score.strictCorrect && score.syntaxValid.contains(false))
        assert(score.diagnostics.get("parse_error_code").contains(code), s"Generation grade must expose $code: ${score.diagnostics}")
        assert(score.diagnostics.get("parse_error_line").contains("3") && score.diagnostics.get("parse_error_column").contains("1"))
      }
      val named = RenderSpec(SyntaxStyle.NamedEnd, lexicon)
      val validSource = source(lexicon, s"${lexicon.end} ${lexicon.func} n_abcdefgh")
      val otherErrors = Vector(
        (s"${lexicon.func} n_abcdefgh\n", "unclosed_scope", "2"),
        (s"${lexicon.end} ${lexicon.func} n_abcdefgh\n", "underflow", "1"),
        (validSource + "unsupported\n", "unknown_statement", "4")
      )
      otherErrors.foreach { (text, code, line) =>
        val score = GenerationGrader.grade(gold, GradingResponse("completed", text), named)
        assert(score.outcome == Outcome.InvalidGeneratedSyntax && !score.strictCorrect && score.syntaxValid.contains(false))
        assert(score.diagnostics == Map("parse_error_code" -> code, "parse_error_line" -> line, "parse_error_column" -> "1"))
      }
      val extra = GenerationGrader.grade(gold, GradingResponse("completed", validSource + s"${lexicon.nop} p_ijklmnop\n"), named)
      assert(extra.outcome == Outcome.ValidSyntaxWrongAst && !extra.strictCorrect && extra.syntaxValid.contains(true), "A valid extra statement changes the AST rather than causing a parse error")
    }

  def parser(): Unit =
    kindMismatch()
    nameMismatch()
    precedence()
    malformed()

  def run(): Unit =
    parser()
    grading()

  def main(args: Array[String]): Unit =
    if args.isEmpty then run()
    else
      val lexicons = if args.lift(1).contains("nonce") then Lexicon.fixedNonce else Vector(Lexicon.natural)
      args(0) match
        case "kind" => kindMismatch(lexicons)
        case "name" => nameMismatch(lexicons)
        case "precedence" => precedence(lexicons)
        case "malformed" => malformed(lexicons)
        case "grading" => grading(lexicons)
        case other => throw new IllegalArgumentException(s"Unknown diagnostic test: $other")
    println("PASS close diagnostics")
