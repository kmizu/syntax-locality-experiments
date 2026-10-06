package locality.bench.run

final case class Limits(maxGenerationCalls: Int, maxHttpAttempts: Int, localTokenCap: Long):
  require(maxGenerationCalls > 0 && maxHttpAttempts > 0 && localTokenCap > 0, "limits must be positive")

final class Budget(limits: Limits, initialKnown: Long, initialReservations: Vector[(String, Long)]):
  require(initialKnown >= 0 && initialReservations.forall(_._2 >= 0))
  require(initialReservations.map(_._1).distinct.size == initialReservations.size, "Duplicate reservation ID")
  require(initialReservations.map(p => BigInt(p._2)).sum.isValidLong, "Reservation sum exceeds Long")
  private var known = initialKnown
  private var reservations = initialReservations.toMap
  private var calls = 0
  private var attempts = 0
  def start(id: String, reservation: Long): Boolean = synchronized {
    require(reservation > 0)
    if reservations.contains(id) || calls >= limits.maxGenerationCalls || BigInt(known) + reservations.values.map(BigInt(_)).sum + reservation > limits.localTokenCap then false
    else
      reservations += id -> reservation
      calls += 1
      true
  }
  def reserveRetry(id: String, reservation: Long): Boolean = synchronized {
    require(reservation > 0)
    if reservations.contains(id) || BigInt(known) + reservations.values.map(BigInt(_)).sum + reservation > limits.localTokenCap then false
    else
      reservations += id -> reservation
      true
  }
  def cancelUndispatched(id: String): Unit = synchronized { reservations -= id }
  def settle(id: String, usage: Option[Long]): Unit = synchronized {
    usage.foreach { tokens =>
      require(tokens >= 0)
      known = Math.addExact(known, tokens)
      reservations -= id
    }
  }
  def http(): Boolean = synchronized {
    if attempts >= limits.maxHttpAttempts then false
    else
      attempts += 1
      true
  }
  def knownTokens: Long = synchronized(known)
  def reservedTokens: Long = synchronized(reservations.values.sum)
  def logicalCalls: Int = synchronized(calls)
  def httpAttempts: Int = synchronized(attempts)
  def httpAvailable: Boolean = synchronized(attempts < limits.maxHttpAttempts)
