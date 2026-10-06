package locality.bench.lang

enum Kind:
  case UnitScope, FuncScope, AreaScope
  def natural: String = this match
    case UnitScope => "unit"
    case FuncScope => "func"
    case AreaScope => "area"

enum Stmt:
  case Let(variable: String, value: Int)
  case Nop(payload: String)
  case Scope(kind: Kind, name: String, body: Vector[Stmt])

final case class Program(body: Vector[Stmt])
final case class CutPoint(eventCount: Int, probeId: String, variable: String)
final case class Observation(value: Int, activeScopes: Vector[String])
final case class OracleError(code: String, message: String)

enum ParsedEvent:
  case Open(kind: Kind, name: String)
  case Close
  case Let(variable: String, value: Int)
  case Nop(payload: String)

final case class ParsedPrefix(events: Vector[ParsedEvent], probeId: String, variable: String)
