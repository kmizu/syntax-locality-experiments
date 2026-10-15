package locality.bench

import locality.bench.data.*
import locality.bench.lang.*
import locality.bench.prompt.*

object PromptTests:
  def run(): Unit =
    val family = Generator.smokeFamilies(1)
    for task <- Task.values do
      val testCases = Generator.expandFamily(family, task, if task == Task.AstToSource then None else Some(View.AfterClose), Lexicon.natural)
      val examples = testCases.map(test => Examples.forCase(test, 919L))
      assert(examples.forall(_.size == 8), "exactly 8 isolated examples")
      val otherTest = testCases.head.copy(family = Generator.smokeFamilies(2))
      assert(Examples.forCase(otherTest, 919L).map(_.family.program) == examples.head.map(_.family.program), "fixed task bank across unrelated test families")
      assert(examples.map(_.map(_.family.program)).distinct.size == 1, "same example ASTs and order across syntax")
      assert(examples.head.map(_.family.program).forall(p => Generator.structureHash(p) != Generator.structureHash(family.program)))
      assert(examples.head.flatMap(e => Generator.names(e.family.program)).toSet.intersect(Generator.names(family.program)).isEmpty)
      for (test, bank) <- testCases.zip(examples) do
        val built = PromptBuilder.describe(test, bank)
        val request = PromptBuilder.build(test, bank, "test-model", Some("low"), 8192)
        assert(request.input.map(_.content).mkString == built.text)
        assert(request.maxOutputTokens == 8192 && request.reasoningEffort == Some("low"))
        assert((1 to 8).forall(i => built.text.contains(s"Example $i\n")))
        assert(!built.text.contains("<test") && !built.text.contains("<example") && !built.text.contains("... exactly"))
        assert(!built.text.contains("family-") && !built.text.contains("smoke-"))
        // Research condition labels never appear as whole tokens in a prompt (the word "indent" is only used inflected).
        assert(!SyntaxStyle.values.exists(style => ("(?<![A-Za-z_])" + style.id + "(?![A-Za-z_])").r.findFirstIn(built.text).nonEmpty), s"style id leaked into ${test.spec.style}")
        val grammar = built.text.split("\nSYNTAX\n", -1).last.split("\nEXAMPLES\n", -1).head
        test.spec.style match
          case SyntaxStyle.DepthEnd =>
            assert(grammar.contains("Close the innermost open block with end K on one line. K is the nesting depth of the block being closed: 1 for a block opened at top level, 2 for a block opened directly inside a depth 1 block, and so on.\n"))
            assert(grammar.contains("Indentation has no meaning; scope is determined solely by explicit opening and closing lines.\n"))
          case SyntaxStyle.Indent =>
            assert(grammar.contains("Open a block with KIND NAME on one line.\nBlocks have no closing line; see the indentation rule below.\n"))
            assert(grammar.contains("Use one statement per line. Indentation is significant. Each line inside a block is indented by exactly two more spaces than the line that opened the block; top-level lines are not indented. A block ends when a line with less indentation appears or when the program ends. There is no closing line.\n"))
            assert(!grammar.contains("Indentation has no meaning"))
            assert(built.metrics.closeStart.isEmpty && built.metrics.closeEnd.isEmpty, "indent style has no closing span")
          case SyntaxStyle.IndentNamedEnd | SyntaxStyle.IndentGenericEnd =>
            val closing = if test.spec.style == SyntaxStyle.IndentNamedEnd then
              "Close the innermost open block with end KIND NAME on its own line, indented exactly like that block's opening line. KIND and NAME must repeat the block's opening keyword and name.\n"
            else "Close the innermost open block with end on its own line, indented exactly like that block's opening line.\n"
            assert(grammar.contains("Open a block with KIND NAME on one line.\n" + closing), s"${test.spec.style} closing sentence")
            assert(grammar.contains("Use one statement per line. Indentation is significant. Each line inside a block is indented by exactly two more spaces than the line that opened the block; top-level lines are not indented. The closing line of a block uses the same indentation as its opening line, and indentation and closing lines must agree.\n"))
            assert(!grammar.contains("Indentation has no meaning") && !grammar.contains("There is no closing line") && !grammar.contains("when the program ends"))
            if task != Task.AstToSource && test.view.contains(View.AfterClose) then
              assert(built.metrics.closeStart.isDefined && built.metrics.closeEnd.isDefined, s"${test.spec.style} has a closing span")
          case _ =>
            assert(grammar.contains("Indentation has no meaning; scope is determined solely by explicit opening and closing lines.\n"))
        if task != Task.AstToSource then
          assert(built.metrics.probe.isDefined && built.metrics.opener.isDefined && built.metrics.binding.isDefined, s"source offsets for ${test.spec.style}")
          val sourceLines = test.source.linesIterator.toVector
          assert(sourceLines(built.metrics.probe.get.line - 1).trim.startsWith("probe "))
          assert(sourceLines(built.metrics.opener.get.line - 1).trim.split(" ")(1) == test.family.focusedName)
          assert(sourceLines(built.metrics.binding.get.line - 1).trim.startsWith("let x_a "))
          if test.spec.style != SyntaxStyle.Indent then assert(built.metrics.closeStart.isDefined == (test.view.contains(View.AfterClose)))
        val testSection = built.text.split("\nTEST\n", -1).last
        if task == Task.AstToSource then
          assert(AstTable.parse(testSection.stripPrefix("AST table:\n").stripSuffix("Program:\n").trim).isRight)
        else
          assert(testSection == "Program prefix:\n" + test.source + "Answer:\n")
          assert(testSection.count(_ == '\n') == test.source.count(_ == '\n') + 2)
          assert(!testSection.contains(s"\n${test.goldObservation.value}\n"), "gold excluded from test request")
        assert(built.metrics.sourceChars > 0 && built.metrics.sourceBytes >= built.metrics.sourceChars)
        built.metrics.closeStart.foreach { start =>
          assert(built.metrics.closeEnd.exists(_.charOffset > start.charOffset), "close span includes the final closing line")
          assert(built.metrics.closeEnd == built.metrics.probe, "close span ends immediately before probe")
        }
    val named = testCasesFor(family, Lexicon.fixedNonce(0))
    val built = PromptBuilder.describe(named, Examples.forCase(named, 919L))
    assert(built.text.contains("daxu") && built.text.contains("creates a local scope"))
    val before = named.copy(view = Some(View.BeforeClose))
    assert(PromptBuilder.describe(before, Examples.forCase(before, 919L)).metrics.closeStart.isEmpty)
    ImportTests.prompt()

  private def testCasesFor(family: CaseFamily, lexicon: Lexicon): ExpandedCase =
    Generator.expandFamily(family, Task.ScopeLookup, Some(View.AfterClose), lexicon)(3)
  def main(args: Array[String]): Unit = run()
