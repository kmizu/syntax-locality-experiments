package locality.llm.json

private[json] object JsonParser:
  private final case class Invalid(offset: Int, reason: String) extends RuntimeException
  def parse(input: String): Either[JsonError, JsonValue] =
    try Right(new Parser(input).parse())
    catch case Invalid(offset, reason) => Left(JsonError(offset, reason))

  private final class Parser(input: String):
    private var pos = 0
    private def fail(reason: String): Nothing = throw Invalid(pos, reason)
    private def ws(): Unit =
      while pos < input.length && " \t\r\n".contains(input.charAt(pos)) do pos += 1
    private def accept(ch: Char): Boolean =
      if pos < input.length && input.charAt(pos) == ch then
        pos += 1
        true
      else false
    private def expect(ch: Char): Unit = if !accept(ch) then fail(s"Expected '$ch'")
    def parse(): JsonValue =
      ws()
      val value = readValue(0)
      ws()
      if pos != input.length then fail("Extra input after JSON value")
      value
    private def literal(text: String, result: JsonValue): JsonValue =
      if !input.startsWith(text, pos) then fail(s"Expected $text")
      pos += text.length
      result
    private def readValue(depth: Int): JsonValue =
      ws()
      if pos >= input.length then fail("Unexpected end of input")
      input.charAt(pos) match
        case '"' => Str(readString())
        case '[' =>
          if depth >= 128 then fail("Maximum JSON depth 128 exceeded")
          pos += 1
          ws()
          val values = Vector.newBuilder[JsonValue]
          if !accept(']') then
            values += readValue(depth + 1)
            ws()
            while accept(',') do
              values += readValue(depth + 1)
              ws()
            expect(']')
          Arr(values.result())
        case '{' =>
          if depth >= 128 then fail("Maximum JSON depth 128 exceeded")
          pos += 1
          ws()
          val fields = Vector.newBuilder[(String, JsonValue)]
          val seen = scala.collection.mutable.HashSet.empty[String]
          def field(): Unit =
            ws()
            if pos >= input.length || input.charAt(pos) != '"' then fail("Expected an object key")
            val key = readString()
            if !seen.add(key) then fail("Duplicate object key")
            ws()
            expect(':')
            fields += key -> readValue(depth + 1)
            ws()
          if !accept('}') then
            field()
            while accept(',') do field()
            expect('}')
          Obj(fields.result())
        case 't' => literal("true", Bool(true))
        case 'f' => literal("false", Bool(false))
        case 'n' => literal("null", Null)
        case ch if ch == '-' || ch >= '0' && ch <= '9' => readNumber()
        case _ => fail("Expected JSON value")

    private def readString(): String =
      expect('"')
      val start = pos
      val result = new java.lang.StringBuilder
      var closed = false
      while pos < input.length && !closed do
        val ch = input.charAt(pos)
        pos += 1
        ch match
          case '"' => closed = true
          case '\\' =>
            if pos >= input.length then fail("Unexpected end of string escape")
            val escape = input.charAt(pos)
            pos += 1
            escape match
              case '"' => result.append('"')
              case '\\' => result.append('\\')
              case '/' => result.append('/')
              case 'b' => result.append('\b')
              case 'f' => result.append('\f')
              case 'n' => result.append('\n')
              case 'r' => result.append('\r')
              case 't' => result.append('\t')
              case 'u' =>
                if input.length - pos < 4 then fail("Incomplete Unicode escape")
                var unit = 0
                var i = 0
                while i < 4 do
                  val digit = Character.digit(input.charAt(pos), 16)
                  if digit < 0 || input.charAt(pos) > 127 then fail("Invalid Unicode escape")
                  unit = unit * 16 + digit
                  pos += 1
                  i += 1
                result.append(unit.toChar)
              case _ => fail("Invalid string escape")
          case c if c < ' ' => fail("Unescaped control character")
          case c => result.append(c)
      if !closed then fail("Unexpected end of string")
      val text = result.toString
      if !JsonWriter.validUnicode(text) then throw Invalid(start, "Unpaired surrogate")
      text

    private def readNumber(): JsonValue =
      val start = pos
      def digit: Boolean = pos < input.length && input.charAt(pos) >= '0' && input.charAt(pos) <= '9'
      accept('-')
      if accept('0') then ()
      else
        if !digit then fail("Expected digit")
        while digit do pos += 1
      if accept('.') then
        if !digit then fail("Expected fractional digits")
        while digit do pos += 1
      if pos < input.length && (input.charAt(pos) == 'e' || input.charAt(pos) == 'E') then
        pos += 1
        if !accept('+') then accept('-')
        if !digit then fail("Expected exponent digits")
        while digit do pos += 1
      if pos - start > 1024 then fail("Number lexical length exceeds 1024")
      try Num(BigDecimal(input.substring(start, pos)))
      catch case _: NumberFormatException => fail("Number exponent or scale is out of range")
