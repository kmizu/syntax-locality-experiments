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
  /** Brings the `let` bindings made directly inside the already-closed module `target` into the current scope. */
  case Import(target: String)

final case class Program(body: Vector[Stmt]):
  /** True when any statement at any depth is an import; such families carry a qualification condition. */
  def containsImport: Boolean =
    def walk(stmts: Vector[Stmt]): Boolean = stmts.exists {
      case Stmt.Import(_) => true
      case Stmt.Scope(_, _, children) => walk(children)
      case _ => false
    }
    walk(body)
final case class CutPoint(eventCount: Int, probeId: String, variable: String)
final case class Observation(value: Int, activeScopes: Vector[String])
final case class OracleError(code: String, message: String)

enum ParsedEvent:
  case Open(kind: Kind, name: String)
  case Close
  case Let(variable: String, value: Int)
  case Nop(payload: String)
  case Import(target: String)

final case class ParsedPrefix(events: Vector[ParsedEvent], probeId: String, variable: String)
