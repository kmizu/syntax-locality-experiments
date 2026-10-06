package locality.bench

import locality.bench.lang.*

object SyntaxTests:
  def run(): Unit =
    val program = Program(Vector(Stmt.Scope(Kind.FuncScope, "n_abcdefgh", Vector(Stmt.Let("x_a", 1234)))))
    val endings = Vector("}", "end", "end func", "end func n_abcdefgh", "end junk z_aaaaaaaa")
    for (style, ending) <- SyntaxStyle.values.toVector.zip(endings) do
      val spec = RenderSpec(style, Lexicon.natural)
      val opening = if style == SyntaxStyle.Braces then "func n_abcdefgh {" else "func n_abcdefgh"
      val expected = s"$opening\nlet x_a 1234\n$ending\n"
      assert(Renderer.renderProgram(program, spec) == expected, s"golden $style")
      assert(Parser.parseProgram(expected, spec) == Right(program), s"roundtrip $style")
      val prefix = Renderer.renderPrefix(program, CutPoint(2, "q_abcdefgh", "x_a"), spec)
      val parsed = Parser.parsePrefix(prefix, spec).toOption.get
      assert(EventOracle.observe(parsed) == Right(Observation(1234, Vector("n_abcdefgh"))))
      assert(Parser.parseProgram(prefix, spec).isLeft)
      assert(Parser.parsePrefix(prefix + "nop p_abcdefgh\n", spec).isLeft)
      assert(Parser.parseProgram(expected.linesIterator.map("  " + _ + " \t").mkString("\r\n\r\n"), spec) == Right(program))
      assert(Parser.parseProgram(ending + "\n", spec).isLeft, "underflow")
      assert(Parser.parseProgram(opening + "\nlet x_a 1234\n", spec).isLeft, "incomplete full program")
    val named = RenderSpec(SyntaxStyle.NamedEnd, Lexicon.natural)
    assert(Parser.parseProgram("func n_abcdefgh\nend func n_ijklmnop\n", named).isLeft)
    assert(Parser.parseProgram("func n_abcdefgh\nend area n_abcdefgh\n", named).isLeft)
    assert(Parser.parseProgram("func n_abcdefgh\nend area\n", RenderSpec(SyntaxStyle.TypedEnd, Lexicon.natural)).isLeft)
    assert(Parser.parseProgram("func n_abcdefgh\nend junk z_baaaaaaa\n", RenderSpec(SyntaxStyle.PaddedEnd, Lexicon.natural)).isLeft)
    for invalid <- Vector("let x_g 1234", "let x_a 999", "let x_a 10000", "let x_a 1234 extra", "// comment", "func badname", "nop p_bad", "let x_a 1234; nop p_abcdefgh") do
      assert(Parser.parseProgram(invalid, named).isLeft, s"unsupported input rejected: $invalid")
    assert(Parser.parseProgram("func n_abcdefgh\nend func n_abcdefgh\nfunc n_abcdefgh\nend func n_abcdefgh", named).isLeft, "duplicate block names")
    assert(Parser.parseProgram("let x_a 1234\nlet x_a 4321", named).isLeft, "duplicate same-scope binding")
    assert(Lexicon.fixedNonce.size == 8)
    assert(Lexicon.fixedNonce.distinct.size == 8)
    Lexicon.fixedNonce.foreach(l => SyntaxStyle.values.foreach { style =>
      val spec = RenderSpec(style, l)
      assert(Parser.parseProgram(Renderer.renderProgram(program, spec), spec) == Right(program))
    })
    CloseDiagnosticsTests.parser()
  def main(args: Array[String]): Unit = run()
