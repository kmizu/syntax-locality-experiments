package locality.bench

import locality.bench.score.*
import locality.bench.stats.*

object StatsTests:
  def fixture(): Vector[TrialObservation] =
    val baseline = Vector(true, false, true, false)
    val treatment = Vector(true, true, false, true)
    (for
      family <- 0 until 4
      lexical <- Vector("natural", "nonce")
      style <- Vector("generic_end", "named_end")
    yield
      val correct = (if style == "generic_end" then baseline else treatment)(family)
      TrialObservation(s"$family-$lexical-$style", s"f$family", "scope_lookup", "after_close", style,
        lexical, 2, 0, 0, Score(if correct then Outcome.Correct else Outcome.WrongAnswer, correct), true, true)
    ).toVector

  def run(): Unit =
    val rows = fixture()
    val result = PairedAnalysis.compare(rows)
    assert(result.difference.contains(0.25), "B=[1,0,1,0],D=[1,1,0,1] must produce +0.25")
    assert(result.availableFamilies == 4 && result.plannedFamilies == 4)
    val duplicatedReplicates = rows ++ rows.map(r => r.copy(trialId = r.trialId + "-r1", replicate = 1))
    val duplicated = PairedAnalysis.compare(duplicatedReplicates)
    assert(duplicated.availableFamilies == 4 && duplicated.difference == result.difference)
    val partial = rows.map(r => if r.trialId == "0-nonce-named_end" then
      r.copy(score = Score(Outcome.TransportFailure, false)) else r)
    assert(PairedAnalysis.compare(partial).availableFamilies == 3)
    val zeroBaselineError = rows.map(r => r.copy(score = Score(Outcome.Correct, true)))
    assert(PairedAnalysis.compare(zeroBaselineError).relativeErrorReduction.isEmpty)
    assert(PairedAnalysis.compare(zeroBaselineError).discordantFamilies == 0)
    assert(ClusterBootstrap.percentile(Vector(0.0, 10.0, 20.0, 30.0), 0.25) == 7.5)
    val a = ClusterBootstrap.run(result.familyDifferences, 10000, 123L)
    val b = ClusterBootstrap.run(result.familyDifferences, 10000, 123L)
    assert(a == b && a.distribution.size == 10000)
    val zero = ClusterBootstrap.run(PairedAnalysis.compare(zeroBaselineError).familyDifferences, 10000, 123L)
    assert(zero.degenerate && zero.lower == 0.0 && zero.upper == 0.0)
    val strata = Vector(FamilyDifference("x", 1, 0, -1, 1, 0), FamilyDifference("y", 2, 0, 1, 0, 1))
    assert(ClusterBootstrap.run(strata, 20, 1).distribution.forall(_ == 0.0),
      "one-family strata are resampled independently and equally weighted")
    // One populated cell has -1, the other three families each have +1.
    // Equal cell weighting gives zero; pooling all families would incorrectly give +0.5.
    val unequalCells = rows.map { r =>
      val desired = if r.familyId == "f0" then r.style == "generic_end" else r.style == "named_end"
      r.copy(depth = if r.familyId == "f0" then 1 else 2,
        score = Score(if desired then Outcome.Correct else Outcome.WrongAnswer, desired))
    }
    assert(PairedAnalysis.compare(unequalCells).difference.contains(0.0))
    val missingCell = unequalCells.map(r => if r.depth == 1 then r.copy(score = Score(Outcome.TransportFailure, false)) else r)
    assert(PairedAnalysis.compare(missingCell).difference.isEmpty, "no estimate silently drops a wholly missing planned stratum")
    val oneLexical = rows.filter(_.lexicalRegime == "natural")
    assert(PairedAnalysis.compare(oneLexical, lexicalRegimes = Vector("natural")).mcnemar.contains(1.0),
      "three discordant binary pairs with wins 2/losses 1 have exact two-sided p=1")
    val cancellation = rows.filter(_.familyId == "f0").map { r =>
      val correct = (r.style == "named_end") == (r.lexicalRegime == "natural")
      r.copy(score = Score(if correct then Outcome.Correct else Outcome.WrongAnswer, correct))
    }
    val cancelled = PairedAnalysis.compare(cancellation)
    assert(cancelled.difference.contains(0.0) && cancelled.discordantFamilies == 1,
      "opposite lexical disagreements cancel the mean but must remain a discordant family")
