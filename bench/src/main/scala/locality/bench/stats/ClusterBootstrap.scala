package locality.bench.stats

final case class BootstrapResult(
    lower: Double,
    upper: Double,
    iterations: Int,
    seed: Long,
    degenerate: Boolean,
    distribution: Vector[Double]
)

object ClusterBootstrap:
  def percentile(sorted: Vector[Double], p: Double): Double =
    require(sorted.nonEmpty && p >= 0 && p <= 1)
    require(sorted.sliding(2).forall(pair => pair.size < 2 || pair(0) <= pair(1)), "Percentile requires sorted values")
    val position = (sorted.size - 1) * p
    val lo = math.floor(position).toInt
    val hi = math.ceil(position).toInt
    sorted(lo) + (sorted(hi) - sorted(lo)) * (position - lo)
  def run(families: Vector[FamilyDifference], iterations: Int = 10000, seed: Long = 20261005L): BootstrapResult =
    require(families.nonEmpty && iterations > 0)
    require(families.map(_.familyId).distinct.size == families.size, "One cluster per structural family")
    val random = new java.util.Random(seed)
    val strata = families.groupBy(f => (f.depth, f.filler)).toVector.sortBy(_._1).map(_._2.sortBy(_.familyId))
    val distribution = Vector.fill(iterations) {
      strata.map { group =>
        var sum = 0.0
        var i = 0
        while i < group.size do
          sum += group(random.nextInt(group.size)).difference
          i += 1
        sum / group.size
      }.sum / strata.size
    }
    val sorted = distribution.sorted
    BootstrapResult(percentile(sorted, 0.025), percentile(sorted, 0.975), iterations, seed,
      sorted.head == sorted.last, distribution)
