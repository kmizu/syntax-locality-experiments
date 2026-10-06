package locality.bench

import locality.bench.data.*
import locality.bench.lang.*

object DistributionTests:
  def run(): Unit =
    val families = Generator.smokeFamilies
    val cases = families.zipWithIndex.flatMap { (family, i) =>
      for
        task <- Vector(Task.ScopeLookup, Task.ActiveStack)
        view <- View.values.toVector
        lexicon <- Vector(Lexicon.natural, Lexicon.fixedNonce(i))
        expanded <- Generator.expandFamily(family, task, Some(view), lexicon)
      yield expanded
    }
    val result = DatasetAudit.distribution(families, cases)
    assert(result.isRight, "balanced fixed nonce assignments must be accepted")
    val summary = result.toOption.get
    assert(summary.familyCount == 4 && summary.blockCount == 19, "count structural families and all AST blocks once")
    assert(summary.kindCounts == Vector(KindCount(Kind.UnitScope, 4), KindCount(Kind.FuncScope, 9), KindCount(Kind.AreaScope, 6)),
      "handmade kind distribution: imbalance is described without an invented exclusion threshold")
    val depthTwo = summary.kindCells.find(c => c.depth == 2 && c.fillerStatements == 0).get
    assert(depthTwo.familyCount == 2 && depthTwo.counts.map(_.count) == Vector(2L, 4L, 2L))
    assert(summary.nonceCells.size == 6, "separate task and depth/filler cells")
    val nonceTwo = summary.nonceCells.find(c => c.task == Task.ScopeLookup && c.depth == 2).get
    assert(nonceTwo.familyCount == 2 && nonceTwo.counts == Vector(1, 1, 0, 0, 0, 0, 0, 0), "zero-count nonce sets remain in the balance audit")
    assert(DatasetAudit.distribution(families, cases ++ cases) == result, "style/view/replicate duplication must not inflate counts")

    val allZero = cases.map(c => if c.spec.lexicon.regime == "nonce" then c.copy(spec = c.spec.copy(lexicon = Lexicon.fixedNonce(0))) else c)
    assert(DatasetAudit.distribution(families, allZero).isLeft, "two nonce assignments to one set are unbalanced when six sets have zero")
    val mixed = cases.updated(0, cases(0).copy(spec = cases(0).spec.copy(lexicon = Lexicon.fixedNonce(7))))
    assert(DatasetAudit.distribution(families, mixed).isLeft, "same family cannot use different nonce mappings between conditions")
    val wrongMap = cases.map(c => if c.spec.lexicon.regime == "nonce" then c.copy(spec = c.spec.copy(lexicon = c.spec.lexicon.copy(unit = "daxx"))) else c)
    assert(DatasetAudit.distribution(families, wrongMap).isLeft, "fixed lexicon set ID must identify its exact keyword mapping")
    val natural = cases.filter(_.spec.lexicon.regime == "natural")
    assert(DatasetAudit.distribution(families, natural).toOption.get.nonceCells.isEmpty, "natural-only smoke has no nonce allocation")

  def main(args: Array[String]): Unit = run()
