package locality.bench

import locality.bench.lang.*
import locality.bench.data.*

object GeneratorTests:
  def run(): Unit =
    assert(Seeds.derive(123L, "test", 1) != Seeds.derive(123L, "test", 2), "namespaced SHA256 seed")
    val family = Generator.generateFamily(GenerationConfig(4, 8, 1), 42L)
    assert(family == Generator.generateFamily(GenerationConfig(4, 8, 1), 42L), "byte-identical deterministic input")
    assert(DatasetAudit.audit(Vector(family)) == Right(()))
    assert(DatasetAudit.audit(Vector(family.copy(fillerStatements = 999))).isLeft, "reject inconsistent filler metadata")
    assert(DatasetAudit.audit(Vector(family.copy(closeCount = 1))).isLeft, "reject inconsistent close metadata")
    assert(DatasetAudit.audit(Vector(family.copy(after = family.after.copy(eventCount = family.after.eventCount - 1)))).isLeft, "reject incorrect close span")
    assert(DatasetAudit.audit(Vector(family.copy(visibleOwner = Some(family.focusedName)))).isLeft, "reject wrong visible binding owner")
    assert(Generator.smokeFamilies.size == 4)
    assert(DatasetAudit.audit(Generator.smokeFamilies) == Right(()))
    assert(Generator.smokeFamilies.map(f => ReferenceOracle.observe(f.program, f.before).toOption.get.value) == Vector(5916, 5916, 8604, 7824), "hand-audited before gold")
    assert(Generator.smokeFamilies.map(f => ReferenceOracle.observe(f.program, f.after).toOption.get.value) == Vector(1048, 7321, 6332, 2123), "hand-audited after gold")
    assert(Generator.smokeFamilies.map(f => ReferenceOracle.observe(f.program, f.after).toOption.get.activeScopes) == Vector(Vector("n_outeraaa"), Vector("n_outeraaa"), Vector("n_outeraaa"), Vector("n_outeraaa", "n_middleaa")), "hand-audited remaining scopes")
    for i <- 0 until 10000 do
      val f = Generator.generateFamily(GenerationConfig(2 + i % 7, i % 9, i), Seeds.derive(900L, "property", i))
      val before = ReferenceOracle.observe(f.program, f.before).toOption.get
      val after = ReferenceOracle.observe(f.program, f.after).toOption.get
      assert(before.value != after.value)
      assert(after.activeScopes.size == f.peakDepth - f.closeCount)
      assert(!after.activeScopes.exists(f.siblingNames.contains))
      for lexicon <- Vector(Lexicon.natural, Lexicon.fixedNonce(i % 8)); style <- SyntaxStyle.values do
        val spec = RenderSpec(style, lexicon)
        assert(Parser.parseProgram(Renderer.renderProgram(f.program, spec), spec) == Right(f.program))
        for cut <- Vector(f.before, f.after) do
          val parsed = Parser.parsePrefix(Renderer.renderPrefix(f.program, cut, spec), spec).toOption.get
          assert(EventOracle.observe(parsed) == ReferenceOracle.observe(f.program, cut))
      def rewrite(body: Vector[Stmt], renaming: Boolean, changeSibling: Boolean, changeVisible: Boolean, inSibling: Boolean = false): Vector[Stmt] = body.map {
        case Stmt.Let(v, n) => Stmt.Let(if renaming && v == "x_a" then "x_f" else v,
          if (changeSibling && inSibling && v == "x_a") || (changeVisible && n == after.value) then 9999 else n)
        case Stmt.Scope(k, n, b) => Stmt.Scope(k, n, rewrite(b, renaming, changeSibling, changeVisible, inSibling || f.siblingNames.contains(n)))
        case n => n
      }
      val renamed = Program(rewrite(f.program.body, true, false, false))
      assert(ReferenceOracle.observe(renamed, f.after.copy(variable = "x_f")).toOption.get.value == after.value)
      val siblingChanged = Program(rewrite(f.program.body, false, true, false))
      assert(ReferenceOracle.observe(siblingChanged, f.after) == Right(after))
      val visibleChanged = Program(rewrite(f.program.body, false, false, true))
      assert(ReferenceOracle.observe(visibleChanged, f.after).toOption.get == after.copy(value = 9999))
      val nopInserted = Program(Stmt.Nop("p_abcdefgh") +: f.program.body)
      assert(ReferenceOracle.observe(nopInserted, f.after.copy(eventCount = f.after.eventCount + 1)) == Right(after))
    val nonce = (0 until 32).map(i => Generator.nonceLexicon(77L, i)).groupBy(_.setId).view.mapValues(_.size).toMap
    assert(nonce.size == 8 && nonce.values.max - nonce.values.min <= 1)

  def main(args: Array[String]): Unit = run()
