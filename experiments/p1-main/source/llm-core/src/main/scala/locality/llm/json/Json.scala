package locality.llm.json

sealed trait JsonValue
final case class Obj(fields: Vector[(String, JsonValue)]) extends JsonValue:
  def get(key: String): Option[JsonValue] = fields.find(_._1 == key).map(_._2)
final case class Arr(values: Vector[JsonValue]) extends JsonValue
final case class Str(value: String) extends JsonValue
final case class Num(value: BigDecimal) extends JsonValue:
  def toLongExact: Either[String, Long] =
    if value.isWhole && value.isValidLong then Right(value.toLong)
    else Left("Expected an integer within Long range")
final case class Bool(value: Boolean) extends JsonValue
case object Null extends JsonValue
final case class JsonError(offset: Int, reason: String)

object Json:
  def obj(fields: (String, JsonValue)*): Obj = Obj(fields.toVector)
  def parse(input: String): Either[JsonError, JsonValue] = JsonParser.parse(input)
  def render(value: JsonValue): String = JsonWriter.write(value, canonical = false)
  def canonical(value: JsonValue): String = JsonWriter.write(value, canonical = true)
