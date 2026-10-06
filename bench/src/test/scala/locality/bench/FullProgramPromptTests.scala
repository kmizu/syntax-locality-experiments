package locality.bench

import locality.bench.data.*
import locality.bench.lang.*
import locality.bench.prompt.*

object FullProgramPromptTests:
  def run(): Unit =
    val parsing = Task.fromId("source_to_ast")
    assert(parsing.nonEmpty, "manual parsing must be an explicit full-program task")
    val task = parsing.get
    val family = Generator.smokeFamilies(1)
    for lexicon <- Lexicon.natural +: Lexicon.fixedNonce do
      val tests = Generator.expandFamily(family, task, None, lexicon)
      val banks = tests.map(t => Examples.forCase(t, 919L))
      assert(banks.map(_.map(_.family.program)).distinct.size == 1, "five styles share example content and order")
      for (test, bank) <- tests.zip(banks) do
        assert(test.source == Renderer.renderProgram(family.program, test.spec), "parsing receives all closing lines")
        assert(Parser.parseProgram(test.source, test.spec) == Right(family.program), "complete input independently parses")
        val generated = test.copy(task = Task.AstToSource)
        assert(bank.map(_.family.program) == Examples.forCase(generated, 919L).map(_.family.program), "both directions share example ASTs")
        val built = PromptBuilder.describe(test, bank)
        val section = built.text.split("\nTEST\n", -1).last
        assert(section == "Program:\n" + test.source + "AST:\n", "full source only, without test gold")
        assert(!section.contains("probe ") && !section.contains("\"body\""), "no prefix marker or gold JSON in test input")
        assert((1 to 8).forall(i => built.text.contains(s"Example $i\n")))
        assert(built.text.contains("parent-child") && built.text.contains("statement order"), "whole-tree contract is explicit")
        assert(built.text.contains("\"type\":\"scope\"") && built.text.contains("\"type\":\"let\"") && built.text.contains("\"type\":\"nop\""))
        assert(built.metrics.opener.isEmpty && built.metrics.binding.isEmpty && built.metrics.closeStart.isEmpty && built.metrics.closeEnd.isEmpty && built.metrics.probe.isEmpty)
        assert(built.metrics.sourceLines == test.source.linesIterator.size)
        val request = PromptBuilder.build(test, bank, "test-model", Some("low"), 16384)
        assert(request.maxOutputTokens == 16384 && request.input.head.content == built.text)
    assert(scala.util.Try(Generator.expandFamily(family, task, Some(View.AfterClose), Lexicon.natural)).isFailure, "full-program parsing cannot take a cut view")

  def main(args: Array[String]): Unit = run()
