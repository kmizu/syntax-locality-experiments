package locality.bench.stats

import locality.bench.score.*

final case class TokenUsage(
    inputTokens: Option[Long] = None,
    outputTokens: Option[Long] = None,
    reasoningTokens: Option[Long] = None,
    cachedTokens: Option[Long] = None
):
  def totalTokens: Option[Long] = for i <- inputTokens; o <- outputTokens yield i + o
  def nonReasoningOutput: Option[Long] = for o <- outputTokens; r <- reasoningTokens if r <= o yield o - r

final case class TrialObservation(
    trialId: String,
    familyId: String,
    task: String,
    view: String,
    style: String,
    lexicalRegime: String,
    depth: Int,
    filler: Int,
    replicate: Int,
    score: Score,
    dispatched: Boolean,
    terminal: Boolean,
    usage: Option[TokenUsage] = None,
    latencyMs: Option[Long] = None,
    httpAttempts: Int = 0,
    generationAttempts: Int = 0,
    attemptUsage: Vector[TokenUsage] = Vector.empty,
    estimatedCostUsd: Option[Double] = None,
    input: String = "",
    rawAnswer: String = "",
    gold: String = "",
    trialHash: String = ""
)

final case class FamilyDifference(
    familyId: String,
    depth: Int,
    filler: Int,
    difference: Double,
    baselineAccuracy: Double,
    treatmentAccuracy: Double,
    discordant: Boolean = false
)

final case class PairedResult(
    treatment: String,
    baseline: String,
    availableFamilies: Int,
    plannedFamilies: Int,
    difference: Option[Double],
    baselineAccuracy: Option[Double],
    treatmentAccuracy: Option[Double],
    relativeErrorReduction: Option[Double],
    discordantFamilies: Int,
    familyDifferences: Vector[FamilyDifference],
    completeStrata: Boolean,
    missingWorstCase: Option[Double],
    missingBestCase: Option[Double],
    mcnemar: Option[Double]
)

object PairedAnalysis:
  def compare(
      observations: Vector[TrialObservation],
      treatment: String = "named_end",
      baseline: String = "generic_end",
      lexicalRegimes: Vector[String] = Vector("natural", "nonce")
  ): PairedResult =
    require(treatment != baseline && lexicalRegimes.nonEmpty && lexicalRegimes.distinct.size == lexicalRegimes.size)
    require(observations.map(_.trialId).distinct.size == observations.size, "Repeated trial IDs must never be counted twice")
    val rows = observations.filter(r => Set(treatment, baseline)(r.style) && lexicalRegimes.contains(r.lexicalRegime))
    val grouped = rows.groupBy(_.familyId).toVector.sortBy(_._1)
    grouped.foreach { (_, family) =>
      require(family.map(r => (r.depth, r.filler, r.task, r.view)).distinct.size == 1, "A structural family must have fixed task/view/stratum")
      require(family.groupBy(r => (r.lexicalRegime, r.style, r.replicate)).values.forall(_.size == 1),
        "Duplicate family/lexical/style/replicate observations")
    }
    val families = grouped.flatMap { (id, family) =>
      val byLexical = lexicalRegimes.map { lexical =>
        val paired = family.filter(_.lexicalRegime == lexical).groupBy(_.replicate).toVector.sortBy(_._1).flatMap { (_, rs) =>
          for
            t <- rs.find(r => r.style == treatment && r.score.evaluable)
            b <- rs.find(r => r.style == baseline && r.score.evaluable)
          yield (if b.score.strictCorrect then 1.0 else 0.0, if t.score.strictCorrect then 1.0 else 0.0)
        }
        if paired.isEmpty then None
        else Some((mean(paired.map(_._1)), mean(paired.map(_._2)), paired.exists((b, t) => b != t)))
      }
      if byLexical.exists(_.isEmpty) then None
      else
        val b = mean(byLexical.flatten.map(_._1))
        val t = mean(byLexical.flatten.map(_._2))
        Some(FamilyDifference(id, family.head.depth, family.head.filler, t - b, b, t, byLexical.flatten.exists(_._3)))
    }
    val plannedStrata = rows.map(r => (r.depth, r.filler)).distinct.toSet
    val availableStrata = families.map(f => (f.depth, f.filler)).toSet
    val completeStrata = plannedStrata.nonEmpty && plannedStrata == availableStrata
    def equalStrata(values: Vector[FamilyDifference], select: FamilyDifference => Double): Option[Double] =
      if values.isEmpty then None
      else Some(mean(values.groupBy(f => (f.depth, f.filler)).values.toVector.map(fs => mean(fs.map(select)))))
    val difference = if completeStrata then equalStrata(families, _.difference) else None
    val bAccuracy = if completeStrata then equalStrata(families, _.baselineAccuracy) else None
    val tAccuracy = if completeStrata then equalStrata(families, _.treatmentAccuracy) else None
    val sensitivity = Vector(false, true).map { optimistic =>
      val assigned = grouped.map { (id, family) =>
        val lexicalMeans = lexicalRegimes.map { lexical =>
          val rs = family.filter(_.lexicalRegime == lexical)
          val replicates = rs.map(_.replicate).distinct match
            case v if v.isEmpty => Vector(0)
            case v => v
          val differences = replicates.map { replicate =>
            def correctness(style: String): Double =
              rs.find(r => r.style == style && r.replicate == replicate).filter(_.score.evaluable) match
                case Some(r) => if r.score.strictCorrect then 1.0 else 0.0
                case None => if (style == treatment) == optimistic then 1.0 else 0.0
            correctness(treatment) - correctness(baseline)
          }
          mean(differences)
        }
        FamilyDifference(id, family.head.depth, family.head.filler, mean(lexicalMeans), 0, 0)
      }
      equalStrata(assigned, _.difference)
    }
    val eligibleMcNemar = lexicalRegimes.size == 1 && grouped.forall { (_, rs) => rs.map(_.replicate).distinct.size == 1 }
    val mcnemar = if eligibleMcNemar && families.nonEmpty then
      Some(exactMcNemar(families.count(_.difference > 0), families.count(_.difference < 0)))
    else None
    PairedResult(treatment, baseline, families.size, grouped.size, difference, bAccuracy, tAccuracy,
      for b <- bAccuracy if b < 1.0; t <- tAccuracy yield (t - b) / (1.0 - b),
      families.count(f => f.discordant || f.difference != 0), families, completeStrata, sensitivity(0), sensitivity(1), mcnemar)

  private def mean(values: Vector[Double]): Double = values.sum / values.size

  /** Exact two-sided binomial McNemar; only applicable to unaveraged binary family pairs. */
  def exactMcNemar(treatmentWins: Int, baselineWins: Int): Double =
    require(treatmentWins >= 0 && baselineWins >= 0)
    val n = treatmentWins + baselineWins
    if n == 0 then 1.0
    else
      val stop = math.min(treatmentWins, baselineWins)
      var coefficient = BigInt(1)
      var sum = coefficient
      var k = 1
      while k <= stop do
        coefficient = coefficient * (n - k + 1) / k
        sum += coefficient
        k += 1
      math.min(1.0, (BigDecimal(sum * 2) / BigDecimal(BigInt(1) << n)).toDouble)
