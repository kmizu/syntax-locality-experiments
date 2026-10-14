package locality.bench

import locality.bench.lang.*

object SyntaxTests:
  def run(): Unit =
    val program = Program(Vector(Stmt.Scope(Kind.FuncScope, "n_abcdefgh", Vector(Stmt.Let("x_a", 1234)))))
    val endings = Vector("}", "end", "end func", "end func n_abcdefgh", "end junk z_aaaaaaaa", "end 1")
    assert(SyntaxStyle.values.toVector == SyntaxStyle.original ++ Vector(SyntaxStyle.DepthEnd, SyntaxStyle.Indent), "seven styles in fixed order")
    for (style, ending) <- SyntaxStyle.values.toVector.filterNot(_ == SyntaxStyle.Indent).zip(endings) do
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
      // Only the explicit-closer styles ignore leading whitespace; the indent style gives it meaning.
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
      assert(Parser.parseProgram(invalid, RenderSpec(SyntaxStyle.Indent, Lexicon.natural)).isLeft, s"unsupported input rejected by indent: $invalid")
      assert(Parser.parseProgram(invalid, RenderSpec(SyntaxStyle.DepthEnd, Lexicon.natural)).isLeft, s"unsupported input rejected by depth_end: $invalid")
    assert(Parser.parseProgram("func n_abcdefgh\nend func n_abcdefgh\nfunc n_abcdefgh\nend func n_abcdefgh", named).isLeft, "duplicate block names")
    assert(Parser.parseProgram("let x_a 1234\nlet x_a 4321", named).isLeft, "duplicate same-scope binding")
    assert(Lexicon.fixedNonce.size == 8)
    assert(Lexicon.fixedNonce.distinct.size == 8)
    Lexicon.fixedNonce.foreach(l => SyntaxStyle.values.foreach { style =>
      val spec = RenderSpec(style, l)
      assert(Parser.parseProgram(Renderer.renderProgram(program, spec), spec) == Right(program))
    })
    depthEnd()
    indent()
    metamorphic()
    CloseDiagnosticsTests.parser()
    ImportTests.parser()

  private val nested = Program(Vector(
    Stmt.Let("x_a", 1000),
    Stmt.Scope(Kind.UnitScope, "n_outeraaa", Vector(
      Stmt.Let("x_a", 2000),
      Stmt.Scope(Kind.FuncScope, "n_middleaa", Vector(
        Stmt.Let("x_a", 3000),
        Stmt.Scope(Kind.AreaScope, "n_inneraaa", Vector(Stmt.Nop("p_abcdefgh"))),
        Stmt.Nop("p_ijklmnop"))),
      Stmt.Scope(Kind.AreaScope, "n_emptyaaa", Vector.empty),
      Stmt.Let("x_b", 4000))),
    Stmt.Nop("p_qrstuvwx")))

  private def deep(depth: Int): Program =
    def build(level: Int): Stmt =
      val name = "n_" + ('a' + (level - 1)).toChar.toString * 8
      val body = Vector(Stmt.Let("x_a", 1000 + level)) ++ (if level < depth then Vector(build(level + 1)) else Vector(Stmt.Nop("p_abcdefgh")))
      Stmt.Scope(Kind.values(level % 3), name, body)
    Program(Vector(Stmt.Let("x_a", 1000), build(1)))

  private def depthEnd(): Unit =
    val spec = RenderSpec(SyntaxStyle.DepthEnd, Lexicon.natural)
    val golden =
      "let x_a 1000\nunit n_outeraaa\nlet x_a 2000\nfunc n_middleaa\nlet x_a 3000\narea n_inneraaa\nnop p_abcdefgh\nend 3\nnop p_ijklmnop\nend 2\n" +
        "area n_emptyaaa\nend 2\nlet x_b 4000\nend 1\nnop p_qrstuvwx\n"
    assert(Renderer.renderProgram(nested, spec) == golden, "depth_end golden")
    assert(Parser.parseProgram(golden, spec) == Right(nested), "depth_end round trip")
    val nonce = RenderSpec(SyntaxStyle.DepthEnd, Lexicon.fixedNonce(0))
    assert(Renderer.renderProgram(nested, nonce).linesIterator.toVector(7) == "bim 3", "nonce end keyword with depth")
    assert(Parser.parseProgram(Renderer.renderProgram(nested, nonce), nonce) == Right(nested))
    // Prefix after two closes: depth returns to 1 and the oracle sees the outer binding.
    val prefix = Renderer.renderPrefix(nested, CutPoint(10, "q_abcdefgh", "x_a"), spec)
    assert(prefix.endsWith("end 3\nnop p_ijklmnop\nend 2\nprobe q_abcdefgh x_a\n"))
    assert(Parser.parsePrefix(prefix, spec).flatMap(p => EventOracle.observe(p).left.map(e => ParseError(e.code, 0, 0, e.message))) == Right(Observation(2000, Vector("n_outeraaa"))))
    assert(Parser.parsePrefix(prefix, spec).toOption.get.events == locality.bench.data.Generator.events(nested).take(10))
    val wrongDepth = Parser.parseProgram("func n_abcdefgh\nlet x_a 1234\nend 2\n", spec).swap.toOption.get
    assert(wrongDepth.code == "close_depth_mismatch" && wrongDepth.line == 3, s"wrong integer: $wrongDepth")
    assert(Parser.parseProgram("func n_abcdefgh\nlet x_a 1234\nend 0\n", spec).swap.toOption.get.code == "close_depth_mismatch")
    for bad <- Vector("end", "end func", "end 01", "end 1 extra", "end x", "}", "end +1") do
      val error = Parser.parseProgram(s"func n_abcdefgh\nlet x_a 1234\n$bad\n", spec).swap.toOption.get
      assert(error.code == "close_mismatch" && error.line == 3, s"$bad -> $error")
    assert(Parser.parseProgram("func n_abcdefgh\nlet x_a 1234\n", spec).swap.toOption.get.code == "unclosed_scope")
    assert(Parser.parseProgram("end 1\n", spec).swap.toOption.get.code == "underflow")
    val sixteen = deep(16)
    val rendered = Renderer.renderProgram(sixteen, spec)
    assert(rendered.contains("\nend 16\n") && !rendered.contains("\nend 17\n"))
    assert(Parser.parseProgram(rendered, spec) == Right(sixteen), "depth-16 depth_end round trip")
    val cut = CutPoint(locality.bench.data.Generator.events(sixteen).size - 4, "q_abcdefgh", "x_a")
    val deepPrefix = Parser.parsePrefix(Renderer.renderPrefix(sixteen, cut, spec), spec).toOption.get
    assert(EventOracle.observe(deepPrefix) == ReferenceOracle.observe(sixteen, cut))

  private def indent(): Unit =
    val spec = RenderSpec(SyntaxStyle.Indent, Lexicon.natural)
    val golden =
      "let x_a 1000\nunit n_outeraaa\n  let x_a 2000\n  func n_middleaa\n    let x_a 3000\n    area n_inneraaa\n      nop p_abcdefgh\n    nop p_ijklmnop\n" +
        "  area n_emptyaaa\n  let x_b 4000\nnop p_qrstuvwx\n"
    assert(Renderer.renderProgram(nested, spec) == golden, "indent golden")
    assert(Renderer.renderProgram(nested, spec.copy(indentation = false)) == Renderer.renderProgram(nested, spec.copy(indentation = true)), "indent style ignores the indentation flag")
    assert(Parser.parseProgram(golden, spec) == Right(nested), "indent round trip")
    val nonce = RenderSpec(SyntaxStyle.Indent, Lexicon.fixedNonce(0))
    assert(Parser.parseProgram(Renderer.renderProgram(nested, nonce), nonce) == Right(nested))
    // Empty block, trailing whitespace, CRLF, and blank lines are tolerated.
    assert(Parser.parseProgram("unit n_outeraaa\r\n  area n_emptyaaa \t\r\n\r\n  let x_b 4000\r\n", spec) ==
      Right(Program(Vector(Stmt.Scope(Kind.UnitScope, "n_outeraaa", Vector(Stmt.Scope(Kind.AreaScope, "n_emptyaaa", Vector.empty), Stmt.Let("x_b", 4000)))))))
    assert(Parser.parseProgram("unit n_outeraaa\n", spec) == Right(Program(Vector(Stmt.Scope(Kind.UnitScope, "n_outeraaa", Vector.empty)))), "end of input closes an empty block")
    assert(Parser.parseProgram("unit n_outeraaa\n  func n_middleaa\n    let x_a 1234\n", spec).isRight, "end of input closes every open block")
    // Prefix mode: the probe's indentation determines the open blocks.
    val events = locality.bench.data.Generator.events(nested)
    for count <- 1 to events.size do
      val cut = CutPoint(count, "q_abcdefgh", "x_a")
      val prefix = Renderer.renderPrefix(nested, cut, spec)
      val parsed = Parser.parsePrefix(prefix, spec).toOption.getOrElse(throw new AssertionError(s"prefix $count rejected: ${Parser.parsePrefix(prefix, spec)}"))
      assert(parsed.events == events.take(count), s"prefix events $count")
      assert(EventOracle.observe(parsed) == ReferenceOracle.observe(nested, cut), s"prefix oracle $count")
    val prefix10 = Renderer.renderPrefix(nested, CutPoint(10, "q_abcdefgh", "x_a"), spec)
    assert(prefix10.endsWith("      nop p_abcdefgh\n    nop p_ijklmnop\n  probe q_abcdefgh x_a\n"), prefix10)
    assert(Parser.parseProgram(prefix10, spec).swap.toOption.get.code == "unexpected_probe")
    assert(Parser.parsePrefix("unit n_outeraaa\n  let x_a 1234\n", spec).swap.toOption.get.code == "missing_probe")
    assert(Parser.parsePrefix("unit n_outeraaa\n  let x_a 1234\nprobe q_abcdefgh x_a\n  nop p_abcdefgh\n", spec).swap.toOption.get.code == "after_probe")
    def reject(text: String, code: String, line: Int): Unit =
      for parsed <- Vector(Parser.parseProgram(text, spec), Parser.parsePrefix(text + "probe q_abcdefgh x_a\n", spec)) do
        val error = parsed.swap.toOption.getOrElse(throw new AssertionError(s"accepted: $text"))
        assert(error.code == code && error.line == line && error.column == 1, s"$text -> $error (expected $code line $line)")
    reject("unit n_outeraaa\n\tlet x_a 1234\n", "invalid_indent", 2)
    reject("unit n_outeraaa\n \tlet x_a 1234\n", "invalid_indent", 2)
    reject("unit n_outeraaa\n\t  let x_a 1234\n", "invalid_indent", 2)
    reject("unit n_outeraaa\n   let x_a 1234\n", "invalid_indent", 2)
    reject(" let x_a 1234\n", "invalid_indent", 1)
    reject("unit n_outeraaa\n  func n_middleaa\n     let x_a 1234\n", "invalid_indent", 3)
    reject("  let x_a 1234\n", "unexpected_indent", 1)
    reject("unit n_outeraaa\n    let x_a 1234\n", "unexpected_indent", 2)
    reject("unit n_outeraaa\n  let x_a 1234\n    nop p_abcdefgh\n", "unexpected_indent", 3)
    reject("unit n_outeraaa\n  func n_middleaa\n      let x_a 1234\n", "unexpected_indent", 3)
    reject("unit n_outeraaa\n  func n_middleaa\n    let x_a 1234\n   nop p_abcdefgh\n", "invalid_dedent", 4)
    reject("unit n_outeraaa\n  func n_middleaa\n    let x_a 1234\n nop p_abcdefgh\n", "invalid_dedent", 4)
    reject("unit n_outeraaa\n  let x_a 1234\nend\n", "unknown_statement", 3)
    reject("unit n_outeraaa\n  let x_a 1234\n  end unit n_outeraaa\n", "unknown_statement", 3)
    reject("unit n_outeraaa {\n  let x_a 1234\n", "invalid_open", 1)
    reject("unit n_outeraaa\n  let x_a 1234\n}\n", "unknown_statement", 3)
    // A dedent closes blocks; the probe after a dedent sees the outer binding and stack.
    val dedentPrefix = "unit n_outeraaa\n  let x_a 2000\n  func n_middleaa\n    let x_a 3000\n  probe q_abcdefgh x_a\n"
    val parsedDedent = Parser.parsePrefix(dedentPrefix, spec).toOption.get
    assert(parsedDedent.events.count(_ == ParsedEvent.Close) == 1)
    assert(EventOracle.observe(parsedDedent) == Right(Observation(2000, Vector("n_outeraaa"))))
    val twoLevels = Parser.parsePrefix("unit n_outeraaa\n  func n_middleaa\n    let x_a 3000\nlet x_b 1234\nprobe q_abcdefgh x_b\n", spec).toOption.get
    assert(twoLevels.events.count(_ == ParsedEvent.Close) == 2 && EventOracle.observe(twoLevels) == Right(Observation(1234, Vector.empty)))
    val sixteen = deep(16)
    val rendered = Renderer.renderProgram(sixteen, spec)
    assert(rendered.linesIterator.exists(_.startsWith("  " * 16 + "nop ")) && !rendered.linesIterator.exists(_.startsWith("  " * 17)))
    assert(Parser.parseProgram(rendered, spec) == Right(sixteen), "depth-16 indent round trip")
    val deepEvents = locality.bench.data.Generator.events(sixteen)
    for count <- Vector(deepEvents.size - 1, deepEvents.size - 8, deepEvents.size - 16, deepEvents.size) do
      val cut = CutPoint(count, "q_abcdefgh", "x_a")
      val deepPrefix = Parser.parsePrefix(Renderer.renderPrefix(sixteen, cut, spec), spec).toOption.get
      assert(EventOracle.observe(deepPrefix) == ReferenceOracle.observe(sixteen, cut), s"depth-16 prefix $count")

  /** Renaming consistency: the same AST (and its renamed variant) parses to the same Program from every style's rendering. */
  private def metamorphic(): Unit =
    def rename(body: Vector[Stmt]): Vector[Stmt] = body.map {
      case Stmt.Let(v, n) => Stmt.Let(if v == "x_a" then "x_f" else v, n + 1)
      case Stmt.Nop(p) => Stmt.Nop("p_" + p.drop(2).reverse)
      case Stmt.Scope(k, n, b) => Stmt.Scope(k, "n_" + n.drop(2).reverse, rename(b))
    }
    for program <- Vector(nested, deep(16), Program(rename(nested.body)), Program(rename(deep(7).body))) do
      for lexicon <- Lexicon.natural +: Lexicon.fixedNonce do
        val parsed = SyntaxStyle.values.toVector.map { style =>
          val spec = RenderSpec(style, lexicon)
          Parser.parseProgram(Renderer.renderProgram(program, spec), spec)
        }
        assert(parsed.forall(_ == Right(program)), s"every style must parse to the same program for ${lexicon.regime}: $parsed")
        val events = locality.bench.data.Generator.events(program)
        for count <- Vector(0, 1, events.size / 2, events.size - 1, events.size) do
          val cut = CutPoint(count, "q_abcdefgh", "x_a")
          val observed = SyntaxStyle.values.toVector.map { style =>
            val spec = RenderSpec(style, lexicon)
            Parser.parsePrefix(Renderer.renderPrefix(program, cut, spec), spec).toOption.get.events
          }
          assert(observed.distinct.size == 1 && observed.head == events.take(count), s"prefix events differ across styles at $count")
  def main(args: Array[String]): Unit = run()
