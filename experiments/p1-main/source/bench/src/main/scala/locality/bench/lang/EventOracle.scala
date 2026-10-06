package locality.bench.lang

import scala.util.boundary
import boundary.break

object EventOracle:
  def observe(prefix: ParsedPrefix): Either[OracleError, Observation] = boundary:
    var frames = Vector(Map.empty[String, Int])
    var names = Vector.empty[String]
    for event <- prefix.events do event match
      case ParsedEvent.Open(_, name) =>
        frames = frames :+ Map.empty[String, Int]
        names = names :+ name
      case ParsedEvent.Close =>
        if frames.size == 1 then break(Left(OracleError("underflow", "Close in global scope")))
        frames = frames.dropRight(1)
        names = names.dropRight(1)
      case ParsedEvent.Let(variable, value) =>
        if frames.last.contains(variable) then break(Left(OracleError("duplicate_binding", variable)))
        frames = frames.updated(frames.size - 1, frames.last.updated(variable, value))
      case ParsedEvent.Nop(_) => ()
    frames.reverseIterator.flatMap(_.get(prefix.variable)).take(1).toVector.headOption
      .map(v => Right(Observation(v, names)))
      .getOrElse(Left(OracleError("unbound_variable", prefix.variable)))
