package locality.bench.lang

import scala.util.boundary
import boundary.break

object EventOracle:
  /** One open frame: its own bindings, then the imported binding maps in event order. */
  private final case class Frame(own: Map[String, Int], imported: Vector[Map[String, Int]])

  def observe(prefix: ParsedPrefix): Either[OracleError, Observation] = boundary:
    var frames = Vector(Frame(Map.empty, Vector.empty))
    var names = Vector.empty[String]
    // Direct bindings of each closed frame, recorded under the frame's name at its Close event.
    var closed = Map.empty[String, Map[String, Int]]
    for event <- prefix.events do event match
      case ParsedEvent.Open(_, name) =>
        frames = frames :+ Frame(Map.empty, Vector.empty)
        names = names :+ name
      case ParsedEvent.Close =>
        if frames.size == 1 then break(Left(OracleError("underflow", "Close in global scope")))
        closed = closed.updated(names.last, frames.last.own)
        frames = frames.dropRight(1)
        names = names.dropRight(1)
      case ParsedEvent.Let(variable, value) =>
        if frames.last.own.contains(variable) then break(Left(OracleError("duplicate_binding", variable)))
        frames = frames.updated(frames.size - 1, frames.last.copy(own = frames.last.own.updated(variable, value)))
      case ParsedEvent.Nop(_) => ()
      case ParsedEvent.Import(target) =>
        if names.contains(target) then break(Left(OracleError("import_open_module", s"Import of open module $target")))
        val bindings = closed.getOrElse(target, break(Left(OracleError("import_unknown_module", s"Import of unknown or not yet closed module $target"))))
        frames = frames.updated(frames.size - 1, frames.last.copy(imported = frames.last.imported :+ bindings))
    // Innermost frame first; inside a frame its own bindings win, then the latest import.
    def resolve(frame: Frame): Option[Int] =
      frame.own.get(prefix.variable).orElse(frame.imported.reverseIterator.flatMap(_.get(prefix.variable)).take(1).toVector.headOption)
    frames.reverseIterator.flatMap(resolve).take(1).toVector.headOption
      .map(v => Right(Observation(v, names)))
      .getOrElse(Left(OracleError("unbound_variable", prefix.variable)))
