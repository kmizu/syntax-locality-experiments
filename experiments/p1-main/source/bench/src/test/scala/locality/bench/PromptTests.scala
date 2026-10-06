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
        assert(!SyntaxStyle.values.exists(style => built.text.contains(style.id)))
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

  private def testCasesFor(family: CaseFamily, lexicon: Lexicon): ExpandedCase =
    Generator.expandFamily(family, Task.ScopeLookup, Some(View.AfterClose), lexicon)(3)
  def main(args: Array[String]): Unit = run()
