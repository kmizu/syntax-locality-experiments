package locality.bench

import locality.bench.data.*
import locality.bench.lang.*
import locality.bench.prompt.*

object TaskTests:
  def run(): Unit =
    val family = Generator.smokeFamilies.head
    SyntaxStyle.values.foreach { style =>
      val spec = RenderSpec(style, Lexicon.natural)
      val lookup = ExpandedCase(family, Task.ScopeLookup, Some(View.AfterClose), spec)
      val stack = ExpandedCase(family, Task.ActiveStack, Some(View.AfterClose), spec)
      assert(lookup.source == stack.source, "T1 and T2 must use identical prefix")
      val generation = ExpandedCase(family, Task.AstToSource, None, spec)
      val request = PromptBuilder.build(generation, Examples.forCase(generation, 20261005), "gpt-5.6-terra", Some("low"), 16384)
      assert(request.maxOutputTokens == 16384)
      assert(request.input.head.content.contains("parent") && request.input.head.content.contains("order"))
      assert(Parser.parseProgram(generation.goldSource, spec) == Right(family.program))
    }
    TableTests.run()
    ScoreTests.run()
