package locality.llm

import locality.llm.json.*

object JsonTests:
  private def parsed(s: String): JsonValue = Json.parse(s).fold(e => throw new AssertionError(e), identity)
  def register(): Unit =
    TestHarness.test("json", "Escapes, Japanese, and supplementary Unicode round trip") {
      val value = Str("quoted\" slash\\\n\t\r\b\f\u0000 日本語 😀")
      assert(parsed(Json.render(value)) == value)
      assert(parsed("\"\\uD83D\\uDE00\"") == Str("😀"))
      assert(Json.render(Str("\n\t\u0001")) == "\"\\n\\t\\u0001\"")
    }
    TestHarness.test("json", "Literals, empty containers, and nested object parse") {
      assert(parsed(" {\"a\":[true,false,null,{},[]]} \r\n") ==
        Json.obj("a" -> Arr(Vector(Bool(true), Bool(false), Null, Obj(Vector.empty), Arr(Vector.empty)))))
    }
    TestHarness.test("json", "Numbers preserve decimal, exponent, and beyond Long precision") {
      assert(parsed("-12.50e2") == Num(BigDecimal(-1250)))
      assert(parsed("9223372036854775808") == Num(BigDecimal("9223372036854775808")))
      assert(Num(BigDecimal("9223372036854775808")).toLongExact.isLeft)
      assert(Num(BigDecimal("1.1")).toLongExact.isLeft)
      assert(Num(BigDecimal(Long.MaxValue)).toLongExact == Right(Long.MaxValue))
      assert(Num(BigDecimal("12e2")).toLongExact == Right(1200L))
    }
    TestHarness.test("json", "Reject trailing commas, EOF, malformed escape, extra input, and invalid numbers") {
      Vector("[1,]", "{\"a\":1,}", "[", "\"\\x\"", "true false", "01", "+1", "1.", "1e", "-.1", "NaN", "\"line\nbreak\"").foreach { text =>
        assert(Json.parse(text).isLeft, text)
      }
    }
    TestHarness.test("json", "Reject lone surrogate in escaped and raw JSON and in writer") {
      Vector("\"\\uD800\"", "\"\\uDC00\"", "\"\\uD800a\"", "\"" + 0xd800.toChar + "\"").foreach { s =>
        assert(Json.parse(s).isLeft, s)
      }
      var rejected = false
      try Json.render(Str(0xdc00.toChar.toString))
      catch case _: IllegalArgumentException => rejected = true
      assert(rejected)
    }
    TestHarness.test("json", "Reject duplicate keys including equivalent Unicode escapes") {
      assert(Json.parse("{\"a\":1,\"a\":2}").isLeft)
      assert(Json.parse("{\"a\":1,\"\\u0061\":2}").isLeft)
    }
    TestHarness.test("json", "Depth 128 and number token 1024 boundaries enforced") {
      assert(Json.parse("[" * 128 + "0" + "]" * 128).isRight)
      assert(Json.parse("[" * 129 + "0" + "]" * 129).isLeft)
      assert(Json.parse("1" * 1024).isRight)
      assert(Json.parse("1" * 1025).isLeft)
    }
    TestHarness.test("json", "Canonical object ordering is recursive and normalizes numeric scale") {
      val value = Json.obj("z" -> Num(BigDecimal("1.00")), "a" -> Json.obj("z" -> Bool(true), "a" -> Str("x")))
      assert(Json.canonical(value) == "{\"a\":{\"a\":\"x\",\"z\":true},\"z\":1}")
      assert(Json.render(value).startsWith("{\"z\":"))
    }
