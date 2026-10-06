package locality.bench.run

import locality.llm.json.*
import locality.llm.*
import java.security.MessageDigest
import java.nio.charset.StandardCharsets.UTF_8

object JsonSupport:
  def parse(text: String): JsonValue = Json.parse(text).fold(e => throw new IllegalArgumentException(s"JSON at ${e.offset}: ${e.reason}"), identity)
  def obj(value: JsonValue): Obj = value match
    case o: Obj => o
    case _ => throw new IllegalArgumentException("Expected JSON object")
  def field(value: JsonValue, key: String): JsonValue = obj(value).get(key).getOrElse(throw new IllegalArgumentException(s"Missing $key"))
  def string(value: JsonValue): String = value match
    case Str(v) => v
    case _ => throw new IllegalArgumentException("Expected string")
  def str(value: JsonValue, key: String): String = string(field(value, key))
  def long(value: JsonValue): Long = value match
    case n: Num => n.toLongExact.fold(e => throw new IllegalArgumentException(e), identity)
    case _ => throw new IllegalArgumentException("Expected integer")
  def num(value: JsonValue, key: String): Long = long(field(value, key))
  def bool(value: JsonValue): Boolean = value match
    case Bool(v) => v
    case _ => throw new IllegalArgumentException("Expected boolean")
  def arr(value: JsonValue): Vector[JsonValue] = value match
    case Arr(v) => v
    case _ => throw new IllegalArgumentException("Expected array")
  def optionalString(value: JsonValue, key: String): Option[String] = obj(value).get(key).filter(_ != Null).map(string)
  def n(value: Long): Num = Num(BigDecimal(value))
  def s(value: String): Str = Str(value)
  def opt(value: Option[String]): JsonValue = value.map(s).getOrElse(Null)
  def sha(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.getBytes(UTF_8)).map(b => f"${b & 255}%02x").mkString
  def hash(value: JsonValue): String = sha(Json.canonical(value))
  def requestJson(request: LlmRequest): Obj = ResponsesRequestJson.generation(request)
  def requestFrom(value: JsonValue): LlmRequest = LlmRequest(str(value, "model"), str(value, "instructions"),
    arr(field(value, "input")).map(m => TextMessage(str(m, "role"), str(m, "content"))),
    obj(value).get("reasoning").flatMap(v => optionalString(v, "effort")), num(value, "max_output_tokens").toInt)
