package locality.bench.prompt

import locality.bench.lang.*
import locality.llm.json.*
import scala.collection.mutable

final case class AstJsonError(code: String, path: String, message: String)

/** The neutral full-program answer schema. Array order and scope parents are significant. */
object AstJson:
  def encode(program: Program): JsonValue = Json.obj("body" -> encodeBody(program.body))
  def render(program: Program): String = Json.canonical(encode(program))

  private def encodeBody(body: Vector[Stmt]): Arr = Arr(body.map {
    case Stmt.Scope(kind, name, children) => Json.obj(
      "type" -> Str("scope"), "kind" -> Str(kind.natural), "name" -> Str(name), "body" -> encodeBody(children))
    case Stmt.Let(variable, value) => Json.obj("type" -> Str("let"), "variable" -> Str(variable), "value" -> Num(BigDecimal(value)))
    case Stmt.Nop(payload) => Json.obj("type" -> Str("nop"), "payload" -> Str(payload))
  })

  def decode(text: String): Either[AstJsonError, Program] =
    Json.parse(text) match
      case Left(error) => Left(AstJsonError("invalid_json", "$", s"Invalid JSON at offset ${error.offset}: ${error.reason}"))
      case Right(value) =>
        try Right(new Decoder().program(value))
        catch case invalid: Invalid => Left(invalid.error)

  private final class Invalid(val error: AstJsonError) extends RuntimeException(error.message)
  private def fail(code: String, path: String, message: String): Nothing =
    throw new Invalid(AstJsonError(code, path, message))

  private final class Decoder:
    private val names = mutable.HashSet.empty[String]

    def program(value: JsonValue): Program =
      val root = obj(value, "$")
      fields(root, Vector("body"), "$")
      Program(body(field(root, "body", "$"), "$.body"))

    private def body(value: JsonValue, path: String): Vector[Stmt] =
      val values = value match
        case Arr(children) => children
        case _ => fail("type_mismatch", path, "Expected an array")
      val variables = mutable.HashSet.empty[String]
      values.zipWithIndex.map { (value, index) =>
        val nodePath = s"$path[$index]"
        val node = obj(value, nodePath)
        string(field(node, "type", nodePath), s"$nodePath.type") match
          case "scope" =>
            fields(node, Vector("type", "kind", "name", "body"), nodePath)
            val kind = string(field(node, "kind", nodePath), s"$nodePath.kind") match
              case "unit" => Kind.UnitScope
              case "func" => Kind.FuncScope
              case "area" => Kind.AreaScope
              case _ => fail("unknown_kind", s"$nodePath.kind", "Expected unit, func or area")
            val name = lexical(field(node, "name", nodePath), s"$nodePath.name", "n_[a-z]{8}", "invalid_name")
            if !names.add(name) then fail("duplicate_name", s"$nodePath.name", "Scope names must be globally unique")
            Stmt.Scope(kind, name, body(field(node, "body", nodePath), s"$nodePath.body"))
          case "let" =>
            fields(node, Vector("type", "variable", "value"), nodePath)
            val variable = lexical(field(node, "variable", nodePath), s"$nodePath.variable", "x_[a-f]", "invalid_variable")
            if !variables.add(variable) then fail("duplicate_variable", s"$nodePath.variable", "Let variables must be unique in each body")
            val valuePath = s"$nodePath.value"
            val integer = field(node, "value", nodePath) match
              case Num(number) if number.isWhole && number >= 1000 && number <= 9999 => number.toInt
              case _: Num => fail("invalid_value", valuePath, "Expected an integer from 1000 through 9999")
              case _ => fail("type_mismatch", valuePath, "Expected a number")
            Stmt.Let(variable, integer)
          case "nop" =>
            fields(node, Vector("type", "payload"), nodePath)
            Stmt.Nop(lexical(field(node, "payload", nodePath), s"$nodePath.payload", "p_[a-z]{8}", "invalid_payload"))
          case _ => fail("invalid_node_type", s"$nodePath.type", "Expected scope, let or nop")
      }

    private def obj(value: JsonValue, path: String): Obj = value match
      case objectValue: Obj => objectValue
      case _ => fail("type_mismatch", path, "Expected an object")

    private def field(value: Obj, name: String, path: String): JsonValue =
      value.get(name).getOrElse(fail("missing_field", s"$path.$name", "Required field is missing"))

    private def fields(value: Obj, expected: Vector[String], path: String): Unit =
      expected.foreach(name => field(value, name, path))
      value.fields.map(_._1).filterNot(expected.contains).sorted.headOption.foreach { name =>
        val fieldPath = if name.matches("[A-Za-z_][A-Za-z0-9_]*") then s"$path.$name" else s"$path[${Json.canonical(Str(name))}]"
        fail("unknown_field", fieldPath, "Unknown field")
      }

    private def string(value: JsonValue, path: String): String = value match
      case Str(text) => text
      case _ => fail("type_mismatch", path, "Expected a string")

    private def lexical(value: JsonValue, path: String, pattern: String, code: String): String =
      val text = string(value, path)
      if !text.matches(pattern) then fail(code, path, s"Expected $pattern")
      text
