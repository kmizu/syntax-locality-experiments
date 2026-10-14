package locality.bench

import locality.bench.data.*
import locality.bench.lang.*
import locality.bench.prompt.*
import locality.bench.run.*
import locality.bench.run.JsonSupport.*
import locality.bench.score.*
import locality.llm.json.*
import java.nio.file.{Files, Path}

/** P3b import extension: oracles, parser, table, prompt, planner/protocol, grader, and generator audit. */
object ImportTests:
  private val allLexicons = Lexicon.natural +: Lexicon.fixedNonce
  private def let(v: String, n: Int): Stmt = Stmt.Let(v, n)
  private def scope(k: Kind, name: String, body: Stmt*): Stmt = Stmt.Scope(k, name, body.toVector)
  /** A closed library with a decoy module and a source module, both binding x_a. */
  private val library = scope(Kind.UnitScope, "n_librarya", scope(Kind.AreaScope, "n_decoyaaa", let("x_a", 1111)), scope(Kind.FuncScope, "n_sourceaa", let("x_a", 2222)))
  private def program(main: Stmt*): Program = Program(Vector(let("x_a", 1000), let("x_b", 4444), library) ++ main)
  // Event indices: let=1, let=2, open lib=3, open decoy=4, let=5, close=6, open source=7, let=8, close=9, close lib=10, open main=11.
  private val mainOpen = 11
  private def prefix(p: Program, count: Int, variable: String = "x_a"): ParsedPrefix = ParsedPrefix(Generator.events(p).take(count), "q_abcdefgh", variable)
  private def both(p: Program, count: Int, variable: String = "x_a"): Either[OracleError, Observation] =
    val reference = ReferenceOracle.observe(p, CutPoint(count, "q_abcdefgh", variable))
    val event = EventOracle.observe(prefix(p, count, variable))
    // Error messages are free text; codes and observations must agree.
    assert(reference.left.map(_.code) == event.left.map(_.code), s"oracles disagree at $count: $reference vs $event")
    reference

  def oracles(): Unit =
    val shadowed = program(scope(Kind.FuncScope, "n_mainaaaa", Stmt.Import("n_sourceaa"), let("x_a", 3333), Stmt.Nop("p_abcdefgh")))
    assert(both(shadowed, mainOpen + 1) == Right(Observation(2222, Vector("n_mainaaaa"))), "import brings the source binding")
    assert(both(shadowed, mainOpen + 2) == Right(Observation(3333, Vector("n_mainaaaa"))), "own let shadows import")
    assert(both(shadowed, mainOpen + 3) == Right(Observation(3333, Vector("n_mainaaaa"))))
    assert(both(shadowed, mainOpen + 4) == Right(Observation(1000, Vector.empty)), "closing the importer restores the outer binding")
    assert(both(shadowed, mainOpen) == Right(Observation(1000, Vector("n_mainaaaa"))), "before the import the outer binding is visible")
    val laterWins = program(scope(Kind.FuncScope, "n_mainaaaa", Stmt.Import("n_decoyaaa"), Stmt.Import("n_sourceaa"), Stmt.Nop("p_abcdefgh")))
    assert(both(laterWins, mainOpen + 3) == Right(Observation(2222, Vector("n_mainaaaa"))), "later import wins")
    assert(both(laterWins, mainOpen + 1) == Right(Observation(1111, Vector("n_mainaaaa"))))
    val earlierLoses = program(scope(Kind.FuncScope, "n_mainaaaa", Stmt.Import("n_sourceaa"), Stmt.Import("n_decoyaaa"), Stmt.Nop("p_abcdefgh")))
    assert(both(earlierLoses, mainOpen + 3) == Right(Observation(1111, Vector("n_mainaaaa"))), "later import wins regardless of source order in the library")
    val fallback = program(scope(Kind.FuncScope, "n_mainaaaa", Stmt.Import("n_sourceaa"), scope(Kind.AreaScope, "n_inneraaa", Stmt.Nop("p_abcdefgh"))))
    assert(both(fallback, mainOpen + 1, "x_b") == Right(Observation(4444, Vector("n_mainaaaa"))), "a variable missing from the import falls back to the outer scope")
    assert(both(fallback, mainOpen + 3) == Right(Observation(2222, Vector("n_mainaaaa", "n_inneraaa"))), "an inner scope inherits the enclosing import")
    assert(both(fallback, mainOpen + 3, "x_b") == Right(Observation(4444, Vector("n_mainaaaa", "n_inneraaa"))))
    // T2 is unaffected by imports: the stack ignores import statements.
    assert(both(fallback, mainOpen + 3).toOption.get.activeScopes == both(program(scope(Kind.FuncScope, "n_mainaaaa", Stmt.Nop("p_abcdefgh"), scope(Kind.AreaScope, "n_inneraaa", Stmt.Nop("p_abcdefgh")))), mainOpen + 3).toOption.get.activeScopes)
    def rejected(p: Program, count: Int, code: String): Unit =
      val reference = ReferenceOracle.observe(p, CutPoint(count, "q_abcdefgh", "x_a"))
      val event = EventOracle.observe(prefix(p, count))
      assert(reference.swap.toOption.map(_.code).contains(code), s"reference: $reference")
      assert(event.swap.toOption.map(_.code).contains(code), s"event: $event")
    rejected(program(scope(Kind.FuncScope, "n_mainaaaa", Stmt.Import("n_mainaaaa"), Stmt.Nop("p_abcdefgh"))), mainOpen + 2, "import_open_module")
    rejected(program(scope(Kind.FuncScope, "n_mainaaaa", scope(Kind.AreaScope, "n_inneraaa", Stmt.Import("n_mainaaaa"), Stmt.Nop("p_abcdefgh")))), mainOpen + 3, "import_open_module")
    rejected(program(scope(Kind.FuncScope, "n_mainaaaa", Stmt.Import("n_zzzzzzzz"), Stmt.Nop("p_abcdefgh"))), mainOpen + 2, "import_unknown_module")
    val forward = Program(Vector(let("x_a", 1000), Stmt.Import("n_sourceaa"), library, scope(Kind.FuncScope, "n_mainaaaa", Stmt.Nop("p_abcdefgh"))))
    rejected(forward, 3, "import_unknown_module")
    // A cut before a bad import never sees it.
    assert(both(forward, 1) == Right(Observation(1000, Vector.empty)))
    // Both oracles agree at every cut of every valid program.
    for p <- Vector(shadowed, laterWins, earlierLoses, fallback); count <- 0 to Generator.events(p).size do
      both(p, count); both(p, count, "x_b")
    assert(DatasetAudit.importErrors(forward).nonEmpty && DatasetAudit.importErrors(shadowed).isEmpty)

  private val valid = program(scope(Kind.FuncScope, "n_mainaaaa", Stmt.Import("n_sourceaa"), Stmt.Nop("p_abcdefgh")))
  private def namedHead(l: Lexicon): String =
    s"${l.let} x_a 1000\n${l.let} x_b 4444\n${l.unit} n_librarya\n${l.area} n_decoyaaa\n${l.let} x_a 1111\n${l.end} ${l.area} n_decoyaaa\n" +
      s"${l.func} n_sourceaa\n${l.let} x_a 2222\n${l.end} ${l.func} n_sourceaa\n${l.end} ${l.unit} n_librarya\n${l.func} n_mainaaaa\n"
  private def checkError(text: String, spec: RenderSpec, code: String, line: Int): Unit =
    for parsed <- Vector(Parser.parseProgram(text, spec), Parser.parsePrefix(text + "probe q_abcdefgh x_a\n", spec)) do
      val error = parsed.swap.toOption.getOrElse(throw new AssertionError(s"accepted ${spec.style}/${spec.qualification}: $text"))
      assert(error.code == code && error.line == line && error.column == 1, s"${spec.style}/${spec.lexicon.regime}/${spec.qualification}: expected $code line $line; received $error")

  def parser(): Unit =
    for lexicon <- allLexicons do
      for style <- SyntaxStyle.values; qualification <- Qualification.values do
        val spec = RenderSpec(style, lexicon, qualification = qualification)
        val rendered = Renderer.renderProgram(valid, spec)
        assert(Parser.parseProgram(rendered, spec) == Right(valid), s"round trip $style/$qualification/${lexicon.regime}")
        val importLine = rendered.linesIterator.find(_.trim.startsWith(lexicon.importWord + " ")).get.trim
        assert(importLine == (if qualification == Qualification.Fq then s"${lexicon.importWord} n_librarya.n_sourceaa" else s"${lexicon.importWord} n_sourceaa"), importLine)
        for cut <- 0 to Generator.events(valid).size do
          val c = CutPoint(cut, "q_abcdefgh", "x_a")
          assert(Parser.parsePrefix(Renderer.renderPrefix(valid, c, spec), spec).map(_.events) == Right(Generator.events(valid).take(cut)))
      val short = RenderSpec(SyntaxStyle.NamedEnd, lexicon)
      val fq = short.copy(qualification = Qualification.Fq)
      val i = lexicon.importWord
      val tail = s"\n${lexicon.end} ${lexicon.func} n_mainaaaa\n"
      def source(line: String) = namedHead(lexicon) + line + tail
      for malformed <- Vector(i, s"$i n_sourceaa extra", s"$i bad", s"$i n_sourceaa.", s"$i .n_sourceaa", s"$i n_source", s"$i N_SOURCEAA", s"$i n_librarya..n_sourceaa") do
        checkError(source(malformed), short, "invalid_import", 12)
        checkError(source(malformed), fq, "invalid_import", 12)
      checkError(source(s"$i n_librarya.n_sourceaa"), short, "invalid_import", 12)
      checkError(source(s"$i n_sourceaa"), fq, "import_path_mismatch", 12)
      checkError(source(s"$i n_decoyaaa.n_sourceaa"), fq, "import_path_mismatch", 12)
      checkError(source(s"$i n_librarya.n_decoyaaa.n_sourceaa"), fq, "import_path_mismatch", 12)
      checkError(source(s"$i n_mainaaaa.n_sourceaa"), fq, "import_path_mismatch", 12)
      for spec <- Vector(short, fq) do
        checkError(source(s"$i n_mainaaaa"), spec, "import_open_module", 12)
        checkError(source(s"$i n_zzzzzzzz"), spec, "import_unknown_module", 12)
        checkError(namedHead(lexicon) + s"${lexicon.area} n_inneraaa\n$i n_mainaaaa\n${lexicon.end} ${lexicon.area} n_inneraaa$tail", spec, "import_open_module", 13)
      checkError(source(s"$i n_librarya.n_mainaaaa"), fq, "import_open_module", 12)
      checkError(source(s"$i n_mainaaaa.n_zzzzzzzz"), fq, "import_unknown_module", 12)
      // Forward reference: the source closes later than the import.
      checkError(s"${lexicon.let} x_a 1000\n$i n_sourceaa\n", short, "import_unknown_module", 2)
      // An import after a dedent in the indent style refers to the block that just closed.
      val indent = RenderSpec(SyntaxStyle.Indent, lexicon)
      val dedented = s"${lexicon.unit} n_librarya\n  ${lexicon.let} x_a 1111\n$i n_librarya\n"
      assert(Parser.parseProgram(dedented, indent) == Right(Program(Vector(scope(Kind.UnitScope, "n_librarya", let("x_a", 1111)), Stmt.Import("n_librarya")))))
      checkError(s"${lexicon.unit} n_librarya\n  $i n_librarya\n", indent, "import_open_module", 2)
    // Cross-style and cross-qualification equality of the same AST.
    val parsedAll = for lexicon <- allLexicons; style <- SyntaxStyle.values; qualification <- Qualification.values yield
      val spec = RenderSpec(style, lexicon, qualification = qualification)
      Parser.parseProgram(Renderer.renderProgram(valid, spec), spec)
    assert(parsedAll.distinct == Vector(Right(valid)))

  def table(): Unit =
    val rendered = AstTable.render(valid, 903L)
    val importRows = rendered.linesIterator.filter(_.split("\t")(3) == "IMPORT").toVector
    assert(importRows.size == 1 && importRows.head.split("\t").toVector.drop(4) == Vector("-", "n_sourceaa", "-", "-", "-"), s"IMPORT row: $importRows")
    assert(!rendered.contains("n_librarya.n_sourceaa"), "the table never shows a qualified path")
    assert(AstTable.parse(rendered) == Right(valid), "IMPORT round trip")
    assert(AstTable.parse(AstTable.render(valid, 904L)) == Right(valid))
    assert(AstTable.parse(rendered.replace("\tIMPORT\t-\tn_sourceaa", "\tIMPORT\tfunc\tn_sourceaa")).isLeft, "IMPORT rows have no kind")
    assert(AstTable.parse(rendered.replace("IMPORT\t-\tn_sourceaa", "IMPORT\t-\tn_librarya.n_sourceaa")).isLeft, "IMPORT rows take a single name")
    assert(AstTable.parse(rendered.replace("IMPORT\t-\tn_sourceaa", "IMPORT\t-\tn_mainaaaa")).isLeft, "import of the open block")
    assert(AstTable.parse(rendered.replace("IMPORT\t-\tn_sourceaa", "IMPORT\t-\tn_zzzzzzzz")).isLeft, "import of an unknown block")
    val forward = Program(Vector(Stmt.Import("n_sourceaa"), library))
    assert(AstTable.parse(AstTable.render(forward, 905L)).isLeft, "forward import")
    for style <- SyntaxStyle.values; qualification <- Qualification.values do
      val spec = RenderSpec(style, Lexicon.natural, qualification = qualification)
      assert(GenerationGrader.grade(valid, GradingResponse("completed", Renderer.renderProgram(AstTable.parse(rendered).toOption.get, spec)), spec).strictCorrect)

  def grader(): Unit =
    for lexicon <- allLexicons do
      val fq = RenderSpec(SyntaxStyle.NamedEnd, lexicon, qualification = Qualification.Fq)
      val short = RenderSpec(SyntaxStyle.NamedEnd, lexicon)
      val i = lexicon.importWord
      def source(line: String) = namedHead(lexicon) + line + s"\n${lexicon.nop} p_abcdefgh\n${lexicon.end} ${lexicon.func} n_mainaaaa\n"
      assert(GenerationGrader.grade(valid, GradingResponse("completed", source(s"$i n_librarya.n_sourceaa")), fq).strictCorrect)
      assert(GenerationGrader.grade(valid, GradingResponse("completed", source(s"$i n_sourceaa")), short).strictCorrect)
      val wrongPath = GenerationGrader.grade(valid, GradingResponse("completed", source(s"$i n_decoyaaa.n_sourceaa")), fq)
      assert(wrongPath.outcome == Outcome.InvalidGeneratedSyntax && !wrongPath.strictCorrect && wrongPath.syntaxValid.contains(false))
      assert(wrongPath.diagnostics == Map("parse_error_code" -> "import_path_mismatch", "parse_error_line" -> "12", "parse_error_column" -> "1"), wrongPath.diagnostics.toString)
      assert(GenerationGrader.grade(valid, GradingResponse("completed", source(s"$i n_sourceaa")), fq).diagnostics.get("parse_error_code").contains("import_path_mismatch"))
      assert(GenerationGrader.grade(valid, GradingResponse("completed", source(s"$i n_librarya.n_sourceaa")), short).diagnostics.get("parse_error_code").contains("invalid_import"))
      assert(GenerationGrader.grade(valid, GradingResponse("completed", source(s"$i n_mainaaaa")), fq).diagnostics.get("parse_error_code").contains("import_open_module"))
      assert(GenerationGrader.grade(valid, GradingResponse("completed", source(s"$i n_zzzzzzzz")), short).diagnostics.get("parse_error_code").contains("import_unknown_module"))
      // Importing the decoy instead of the source is valid syntax with a different AST.
      val decoy = GenerationGrader.grade(valid, GradingResponse("completed", source(s"$i n_librarya.n_decoyaaa")), fq)
      assert(decoy.outcome == Outcome.ValidSyntaxWrongAst && decoy.syntaxValid.contains(true))

  private def importTarget(p: Program): String =
    def find(body: Vector[Stmt]): Option[String] = body.collectFirst { case Stmt.Import(t) => t }.orElse(body.collect { case Stmt.Scope(_, _, c) => c }.flatMap(find).headOption)
    find(p.body).get
  private def directValue(p: Program, module: String): Int =
    def find(body: Vector[Stmt]): Option[Int] = body.collectFirst { case Stmt.Scope(_, n, c) if n == module => c.collectFirst { case Stmt.Let("x_a", v) => v }.get }
      .orElse(body.collect { case Stmt.Scope(_, _, c) => c }.flatMap(find).headOption)
    find(p.body).get

  def generator(): Unit =
    val families = (0 until 2000).toVector.map(i => Generator.generateFamily(GenerationConfig(2 + i % 7, i % 9, i, imports = true), Seeds.derive(901L, "imports", i)))
    assert(families.map(_.id).distinct.size == families.size)
    assert(families.forall(_.imports) && families.map(_.program).forall(p => DatasetAudit.importErrors(p).isEmpty))
    // Import families are distinct from the import-free families of the same seed, which stay byte-identical.
    val plain = Generator.generateFamily(GenerationConfig(4, 8, 1), 42L)
    assert(plain == Generator.generateFamily(GenerationConfig(4, 8, 1, imports = false), 42L) && !plain.imports)
    assert(Generator.generateFamily(GenerationConfig(4, 8, 1, imports = true), 42L).id != plain.id)
    assert(DatasetAudit.audit(families.take(200)) == Right(()), DatasetAudit.audit(families.take(200)).swap.toOption.map(_.take(5)).toString)
    val broken = families.head
    def replaceImport(body: Vector[Stmt], target: String): Vector[Stmt] = body.map {
      case Stmt.Import(_) => Stmt.Import(target)
      case Stmt.Scope(k, n, c) => Stmt.Scope(k, n, replaceImport(c, target))
      case other => other
    }
    assert(DatasetAudit.audit(Vector(broken.copy(program = Program(replaceImport(broken.program.body, broken.focusedName))))).isLeft, "audit rejects an open import")
    assert(DatasetAudit.audit(Vector(broken.copy(program = Program(replaceImport(broken.program.body, "n_zzzzzzzz"))))).isLeft, "audit rejects an unknown import")
    def swapImport(body: Vector[Stmt], module: String, value: Int): Vector[Stmt] = body.map {
      case Stmt.Import(t) => Stmt.Let("x_a", value)
      case Stmt.Scope(k, n, c) => Stmt.Scope(k, n, swapImport(c, module, value))
      case other => other
    }
    for (f, i) <- families.zipWithIndex do
      val before = ReferenceOracle.observe(f.program, f.before).toOption.get
      val after = ReferenceOracle.observe(f.program, f.after).toOption.get
      val source = importTarget(f.program)
      val sourceValue = directValue(f.program, source)
      assert(before.value != after.value && after.value == sourceValue, s"$i: after value must be the imported binding")
      assert(after.activeScopes.size == f.peakDepth - f.closeCount && !after.activeScopes.exists(f.siblingNames.contains))
      assert(before.activeScopes.lastOption.contains(f.focusedName))
      val chain = Renderer.ancestorChains(f.program)(source)
      assert(chain.size == math.max(1, f.peakDepth / 2) + 1, s"$i: library chain length")
      assert(Generator.names(f.program).size == f.peakDepth + 2 + chain.size + 1)
      for lexicon <- Vector(Lexicon.natural, Lexicon.fixedNonce(i % 8)); style <- SyntaxStyle.values; qualification <- Qualification.values do
        val spec = RenderSpec(style, lexicon, qualification = qualification)
        val rendered = Renderer.renderProgram(f.program, spec)
        assert(Parser.parseProgram(rendered, spec) == Right(f.program), s"$i: round trip $style/$qualification")
        val importLines = rendered.linesIterator.map(_.trim).filter(_.startsWith(lexicon.importWord + " ")).toVector
        assert(importLines == Vector(s"${lexicon.importWord} " + (if qualification == Qualification.Fq then chain.mkString(".") else source)), importLines.toString)
        for cut <- Vector(f.before, f.after) do
          val parsed = Parser.parsePrefix(Renderer.renderPrefix(f.program, cut, spec), spec).toOption.get
          assert(EventOracle.observe(parsed) == ReferenceOracle.observe(f.program, cut), s"$i: oracle agreement $style/$qualification")
      // Renaming metamorphism: variables, block names (including import targets), and payloads.
      def rename(body: Vector[Stmt]): Vector[Stmt] = body.map {
        case Stmt.Let(v, n) => Stmt.Let(if v == "x_a" then "x_f" else v, n)
        case Stmt.Nop(p) => Stmt.Nop("p_" + p.drop(2).reverse)
        case Stmt.Import(t) => Stmt.Import("n_" + t.drop(2).reverse)
        case Stmt.Scope(k, n, b) => Stmt.Scope(k, "n_" + n.drop(2).reverse, rename(b))
      }
      val renamed = Program(rename(f.program.body))
      val renamedAfter = ReferenceOracle.observe(renamed, f.after.copy(variable = "x_f"))
      assert(renamedAfter == Right(after.copy(activeScopes = after.activeScopes.map(n => "n_" + n.drop(2).reverse))), s"$i: renaming")
      assert(EventOracle.observe(Parser.parsePrefix(Renderer.renderPrefix(renamed, f.after.copy(variable = "x_f"), RenderSpec(SyntaxStyle.DepthEnd, Lexicon.natural, qualification = Qualification.Fq)), RenderSpec(SyntaxStyle.DepthEnd, Lexicon.natural, qualification = Qualification.Fq)).toOption.get) == renamedAfter)
      // Replacing the import by a let of the imported value gives the same observation; the decoy's value never matters.
      val inlined = Program(swapImport(f.program.body, source, sourceValue))
      assert(ReferenceOracle.observe(inlined, f.after).toOption.get.value == after.value)
      // The decoy is the library root's other child: it binds x_a to a different value and closes before the source.
      val decoyName = Renderer.ancestorChains(f.program).collectFirst { case (n, c) if c.size == 2 && c.head == chain.head && n != chain(1) => n }
      assert(decoyName.isDefined && directValue(f.program, decoyName.get) != sourceValue, s"$i: decoy module inside the library")
      def setValue(body: Vector[Stmt], module: String, value: Int, inside: Boolean = false): Vector[Stmt] = body.map {
        case Stmt.Let("x_a", _) if inside => Stmt.Let("x_a", value)
        case Stmt.Scope(k, n, c) => Stmt.Scope(k, n, setValue(c, module, value, inside || n == module))
        case other => other
      }
      assert(ReferenceOracle.observe(Program(setValue(f.program.body, decoyName.get, 9999)), f.after) == Right(after), s"$i: decoy value is irrelevant")
      assert(ReferenceOracle.observe(Program(setValue(f.program.body, source, 9999)), f.after).toOption.get == after.copy(value = 9999), s"$i: source value determines the answer")

  def prompt(): Unit =
    val family = Generator.generateFamily(GenerationConfig(4, 8, 1, imports = true), 77L)
    val plain = Generator.smokeFamilies(1)
    for task <- Task.values; qualification <- Qualification.values; lexicon <- Vector(Lexicon.natural, Lexicon.fixedNonce(2)) do
      val view = if task == Task.AstToSource then None else Some(View.AfterClose)
      for test <- Generator.expandFamily(family, task, view, lexicon, qualification = qualification) do
        val examples = Examples.forCase(test, 919L)
        assert(examples.size == 8 && examples.forall(_.family.imports), "import plans use an import example bank")
        assert(examples.forall(e => DatasetAudit.importErrors(e.family.program).isEmpty))
        val built = PromptBuilder.describe(test, examples)
        val text = built.text
        val expectedSyntax = qualification match
          case Qualification.Short => s"An import statement is ${lexicon.importWord} NAME, where NAME is the name of a block that has already been closed.\n"
          case Qualification.Fq => s"An import statement is ${lexicon.importWord} PATH, where PATH is the names of every block enclosing the imported block, from the top level inward, followed by the imported block's own name, joined with dots; the imported block must already be closed.\n"
        val grammar = text.split("\nSYNTAX\n", -1).last.split("\nEXAMPLES\n", -1).head
        assert(grammar.contains(expectedSyntax), s"missing syntax sentence for $qualification/${lexicon.regime}")
        assert(!grammar.contains(if qualification == Qualification.Fq then " NAME, where NAME is the name of a block" else " PATH, where PATH"))
        val intro = text.split("\nSYNTAX\n", -1).head
        assert(intro.contains(PromptBuilder.importSemantics), "introduction states import semantics")
        assert(intro.contains("The named block must already be closed where the import appears"))
        assert(intro.contains("then by that scope's imports with the later import winning, then by the enclosing scopes outward"))
        if task == Task.AstToSource then assert(intro.contains("IMPORT produces an import statement whose target is the block given in its name field.\n"))
        else assert(!intro.contains("IMPORT produces"))
        val exampleSection = text.split("\nEXAMPLES\n", -1).last.split("\nTEST\n", -1).head
        val testSection = text.split("\nTEST\n", -1).last
        if task == Task.AstToSource then
          assert(testSection.linesIterator.exists(_.split("\t").lift(3).contains("IMPORT")), "test table has an IMPORT row")
          assert(!testSection.contains("."), "the table never shows a qualified path")
          val importLines = exampleSection.linesIterator.map(_.trim).filter(_.startsWith(lexicon.importWord + " ")).toVector
          assert(importLines.size == 8, "each example program contains one import")
          assert(importLines.forall(_.contains(".") == (qualification == Qualification.Fq)))
        else
          assert(built.metrics.binding.isDefined && test.source.linesIterator.toVector(built.metrics.binding.get.line - 1).trim.startsWith(lexicon.importWord + " "), "binding offset points at the import line")
          assert(built.metrics.probe.isDefined && built.metrics.opener.isDefined)
        val reversed = PromptBuilder.describe(test.copy(spec = test.spec.copy(qualification = if qualification == Qualification.Fq then Qualification.Short else Qualification.Fq)), examples.map(e => e.copy(spec = e.spec.copy(qualification = if qualification == Qualification.Fq then Qualification.Short else Qualification.Fq))))
        assert(reversed.text != text, "qualification changes the prompt of an import plan")
      // Import-free prompts never mention imports.
      for test <- Generator.expandFamily(plain, task, view, lexicon, qualification = qualification) do
        val text = PromptBuilder.describe(test, Examples.forCase(test, 919L)).text
        assert(!text.toLowerCase.contains("import"), "import-free plans keep their original prompt")
        val fqTest = test.copy(spec = test.spec.copy(qualification = Qualification.Fq))
        assert(PromptBuilder.describe(fqTest, Examples.forCase(fqTest, 919L)).text == text, "qualification does not affect import-free prompts")
    val mixed = Generator.expandFamily(family, Task.ScopeLookup, Some(View.AfterClose), Lexicon.natural).head
    val mismatched = try
      PromptBuilder.describe(mixed, Examples.forCase(mixed.copy(family = plain), 919L).map(_.copy(family = plain)))
      false
    catch case _: IllegalArgumentException => true
    assert(mismatched, "examples must share the import condition")

  /** Families saved by a pre-P3b pilot plan; the generator must still reproduce them exactly. */
  private val savedFamilies = Vector(
    """{"after": {"eventCount": 13, "probeId": "q_chbsprpt", "variable": "x_a"}, "before": {"eventCount": 12, "probeId": "q_gcuxewmr", "variable": "x_a"}, "closeCount": 1, "fillerSeed": 2827947112449664022, "fillerStatements": 0, "focusedName": "n_egbfdupj", "id": "family-04530c98f34024228dd8", "identifierSeed": 4969033936193146896, "peakDepth": 2, "program": "let x_a 5448\nunit n_mniauvdn\nlet x_a 3311\nnop p_mfacphey\nend unit n_mniauvdn\nunit n_qagursux\narea n_qddfhopb\nlet x_a 2261\nnop p_ycxvgall\nend area n_qddfhopb\nfunc n_egbfdupj\nlet x_a 4271\nend func n_egbfdupj\nend unit n_qagursux\n", "siblingNames": ["n_mniauvdn", "n_qddfhopb"], "structureSeed": 5739922212978331505, "targetVariable": "x_a", "valueSeed": -3624081049529726436, "visibleOwner": null}""",
    """{"after": {"eventCount": 26, "probeId": "q_inkmsqhv", "variable": "x_a"}, "before": {"eventCount": 24, "probeId": "q_mjweawuj", "variable": "x_a"}, "closeCount": 2, "fillerSeed": 4968501839135266403, "fillerStatements": 8, "focusedName": "n_egrsyyuw", "id": "family-52923b0698072d22ca9c", "identifierSeed": 4942576272617056326, "peakDepth": 4, "program": "let x_a 3440\narea n_zafwvgrn\nlet x_a 6240\nnop p_xxuurtao\nend area n_zafwvgrn\nfunc n_byqwxdkj\nlet x_a 1904\nunit n_wthmsrjp\nlet x_a 5944\nnop p_tanbotbx\nend unit n_wthmsrjp\nunit n_zvyfjfib\nunit n_eezsppha\nlet x_a 5380\nfunc n_egrsyyuw\nlet x_a 8329\nnop p_prtddxhk\nnop p_zrrvmqlk\nnop p_lwsxpgbj\nnop p_aeoyzpaj\nnop p_rhawrjqd\nnop p_fawihjvr\nnop p_wiqamhln\nnop p_funykgjt\nend func n_egrsyyuw\nend unit n_eezsppha\nend unit n_zvyfjfib\nend func n_byqwxdkj\n", "siblingNames": ["n_zafwvgrn", "n_wthmsrjp"], "structureSeed": 5214648373446125662, "targetVariable": "x_a", "valueSeed": 6136036419388155636, "visibleOwner": "n_byqwxdkj"}""",
    """{"after": {"eventCount": 14, "probeId": "q_voqrlopj", "variable": "x_a"}, "before": {"eventCount": 13, "probeId": "q_ahwkipzk", "variable": "x_a"}, "closeCount": 1, "fillerSeed": -8031025524227586361, "fillerStatements": 0, "focusedName": "n_zxqsezvb", "id": "family-5d733d6cead607cd7bcb", "identifierSeed": -5854947449216223716, "peakDepth": 2, "program": "let x_a 4248\narea n_ehqkxegk\nlet x_a 3221\nnop p_rxwbzsfp\nend area n_ehqkxegk\nunit n_kziffymn\nlet x_a 2126\nunit n_prdnerrt\nlet x_a 7478\nnop p_nxkyikhz\nend unit n_prdnerrt\narea n_zxqsezvb\nlet x_a 6098\nend area n_zxqsezvb\nend unit n_kziffymn\n", "siblingNames": ["n_ehqkxegk", "n_prdnerrt"], "structureSeed": -8334323669132667177, "targetVariable": "x_a", "valueSeed": -634960125537662434, "visibleOwner": "n_kziffymn"}"""
  )

  def planner(): Unit =
    // Pre-P3b saved families are reproduced byte for byte by an import-free configuration.
    savedFamilies.foreach { line =>
      val saved = parse(line)
      val family = Planner.familyFrom(saved)
      val candidates = for ns <- Vector("reading", "generation"); i <- 0 until 4 yield
        Generator.generateFamily(GenerationConfig(family.peakDepth, family.fillerStatements, i), Seeds.derive(20261005L, s"pilot:$ns:${family.peakDepth}:${family.fillerStatements}", i))
      assert(candidates.exists(c => c == family && Json.canonical(Planner.familyJson(c)) == Json.canonical(saved)), s"saved family ${family.id} is no longer reproduced")
    }
    val pilot = Protocol.load("pilot", "p0")
    assert(!pilot.imports && pilot.importQualification == Qualification.Short && !pilot.config.fields.exists(f => f._1 == "imports" || f._1 == "importQualification"))
    val raw = obj(parse(Files.readString(Path.of("configs/pilot.json"))))
    val small = Vector("depths" -> Arr(Vector(n(2), n(4))), "fillersReading" -> Arr(Vector(n(0))), "fillersGeneration" -> Arr(Vector(n(0))), "seedsPerCell" -> n(2))
    def config(extra: (String, JsonValue)*): Path =
      val file = Files.createTempFile("locality-import-config", ".json")
      Files.writeString(file, Json.canonical(Obj(raw.fields.filterNot(f => small.exists(_._1 == f._1) || extra.exists(_._1 == f._1)) ++ small ++ extra)))
      file
    val base = config()
    val explicitFalse = config("imports" -> Bool(false))
    val importsShort = config("imports" -> Bool(true))
    val importsFq = config("imports" -> Bool(true), "importQualification" -> s("fq"))
    val fqWithoutImports = config("importQualification" -> s("fq"))
    try
      val plainProtocol = Protocol.load("pilot", "p1", Some(base))
      val plainTrials = Planner.build(plainProtocol)
      assert(plainTrials.forall(t => !t.expanded.family.imports && t.expanded.id.split(":").length == 6 && !t.metadata.fields.exists(_._1 == "qualification")))
      assert(plainTrials.forall(t => !t.request.input.head.content.toLowerCase.contains("import")))
      def rows(trials: Vector[Trial]) = trials.map(t => (t.expanded.id, Obj(t.metadata.fields.filterNot(_._1 == "id")), Json.canonical(t.gold.copy(fields = t.gold.fields.filterNot(_._1 == "id")))))
      val falseProtocol = Protocol.load("pilot", "p1", Some(explicitFalse))
      assert(falseProtocol.protocolHash != plainProtocol.protocolHash, "an explicit key is part of the protocol identity")
      assert(rows(Planner.build(falseProtocol)) == rows(plainTrials), "absent and explicit false keys plan identical rows")
      val fqOnly = Protocol.load("pilot", "p1", Some(fqWithoutImports))
      assert(fqOnly.importQualification == Qualification.Fq && rows(Planner.build(fqOnly)) == rows(plainTrials), "qualification without imports changes nothing in the plan rows")
      for (file, qualification) <- Vector(importsShort -> Qualification.Short, importsFq -> Qualification.Fq) do
        val p = Protocol.load("pilot", "p1", Some(file))
        assert(p.imports && p.importQualification == qualification)
        val trials = Planner.build(p)
        assert(trials.size == plainTrials.size)
        assert(trials.forall(t => t.expanded.family.imports && t.expanded.id.endsWith(s":${qualification.id}") && t.expanded.id.split(":").length == 7))
        assert(trials.forall(t => str(t.metadata, "qualification") == qualification.id))
        assert(trials.map(_.expanded.family.id).toSet.intersect(plainTrials.map(_.expanded.family.id).toSet).isEmpty, "import families have their own ids")
        trials.foreach { t =>
          val source = str(t.gold, "source")
          val importLine = source.linesIterator.map(_.trim).find(_.startsWith(t.expanded.spec.lexicon.importWord + " ")).get
          assert(importLine.contains(".") == (qualification == Qualification.Fq), importLine)
          assert(Parser.parseProgram(source, t.expanded.spec) == Right(t.expanded.family.program))
          assert(t.request.input.head.content.contains(PromptBuilder.importSyntax(t.expanded.spec)))
        }
        val run = Files.createTempDirectory("locality-import-plan")
        Planner.save(p, run)
        val store = new RunStore(run)
        try
          Planner.audit(store)
          val audit = parse(store.read("dataset-audit.json"))
          assert(arr(field(audit, "fixedNonceMappings")).forall(m => obj(m).get("import").nonEmpty), "import plans record the nonce import keyword")
          assert(store.jsonLines("cases.jsonl").forall(c => str(c, "qualification") == qualification.id))
          assert(Runner.execute(store, new MockClient(store), Limits(trials.size, trials.size * 2, 10000000L), execute = true, mock = true).logicalCalls == trials.size)
          Results.write(store)
          val summary = Files.readString(run.resolve("summary.csv")).linesIterator.toVector.map(_.split(",").toVector.map(_.stripPrefix("\"").stripSuffix("\"")))
          assert(summary.head.take(4) == Vector("task", "view", "style", "qualification"), summary.head.take(4).toString)
          assert(summary.tail.forall(r => r(3) == qualification.id))
          assert(Files.readString(run.resolve("errors.csv")).linesIterator.next().split(",").toVector.map(_.stripPrefix("\"").stripSuffix("\""))(5) == "qualification")
          val scores = store.jsonLines("scores.jsonl")
          assert(scores.nonEmpty && scores.forall(sc => bool(field(sc, "strict_correct"))), "mock gold answers grade as correct under both qualifications")
        finally store.close()
      val plainRun = Files.createTempDirectory("locality-plain-plan")
      Planner.save(plainProtocol, plainRun)
      val plainStore = new RunStore(plainRun)
      try
        val audit = parse(plainStore.read("dataset-audit.json"))
        assert(arr(field(audit, "fixedNonceMappings")).forall(m => obj(m).get("import").isEmpty), "import-free audit files are unchanged")
        assert(plainStore.jsonLines("cases.jsonl").forall(c => obj(c).get("qualification").isEmpty))
      finally plainStore.close()
    finally Vector(base, explicitFalse, importsShort, importsFq, fqWithoutImports).foreach(Files.delete)
    def rejected(body: => Unit): Unit =
      val failed = try
        body
        false
      catch case _: IllegalArgumentException => true
      assert(failed)
    val smokeRaw = obj(parse(Files.readString(Path.of("configs/smoke.json"))))
    for extra <- Vector("imports" -> s("true"), "imports" -> n(1), "importQualification" -> s("full"), "importQualification" -> n(1), "imports" -> Bool(true)) do
      val file = Files.createTempFile("locality-import-invalid", ".json")
      Files.writeString(file, Json.canonical(Obj(smokeRaw.fields :+ extra)))
      try rejected { Protocol.load("smoke", "p0", Some(file)); () }
      finally Files.delete(file)

  def run(): Unit =
    oracles()
    parser()
    table()
    grader()
    generator()
    prompt()
    planner()

  def main(args: Array[String]): Unit = run()
