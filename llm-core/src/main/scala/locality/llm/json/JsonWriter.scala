package locality.llm.json

private[json] object JsonWriter:
  def validUnicode(text: String): Boolean =
    var i = 0
    while i < text.length do
      val ch = text.charAt(i)
      if Character.isHighSurrogate(ch) then
        if i + 1 >= text.length || !Character.isLowSurrogate(text.charAt(i + 1)) then return false
        i += 2
      else if Character.isLowSurrogate(ch) then return false
      else i += 1
    true

  def write(value: JsonValue, canonical: Boolean): String =
    val out = new java.lang.StringBuilder
    def string(text: String): Unit =
      require(validUnicode(text), "Unpaired surrogate in JSON string")
      out.append('"')
      text.foreach {
        case '"' => out.append("\\\"")
        case '\\' => out.append("\\\\")
        case '\b' => out.append("\\b")
        case '\f' => out.append("\\f")
        case '\n' => out.append("\\n")
        case '\r' => out.append("\\r")
        case '\t' => out.append("\\t")
        case ch if ch < ' ' => out.append(f"\\u${ch.toInt}%04x")
        case ch => out.append(ch)
      }
      out.append('"')
    def emit(value: JsonValue, depth: Int): Unit = value match
      case Null => out.append("null")
      case Bool(v) => out.append(if v then "true" else "false")
      case Num(v) =>
        val number = if canonical then v.bigDecimal.stripTrailingZeros.toString else v.toString
        require(number.length <= 1024, "Number lexical length exceeds 1024")
        out.append(number)
      case Str(v) => string(v)
      case Arr(values) =>
        require(depth < 128, "Maximum JSON depth 128 exceeded")
        out.append('[')
        values.zipWithIndex.foreach { (v, i) =>
          if i != 0 then out.append(',')
          emit(v, depth + 1)
        }
        out.append(']')
      case Obj(fields) =>
        require(depth < 128, "Maximum JSON depth 128 exceeded")
        require(fields.map(_._1).distinct.size == fields.size, "Duplicate object key")
        out.append('{')
        val selected = if canonical then fields.sortBy(_._1) else fields
        selected.zipWithIndex.foreach { (entry, i) =>
          if i != 0 then out.append(',')
          string(entry._1)
          out.append(':')
          emit(entry._2, depth + 1)
        }
        out.append('}')
    emit(value, 0)
    out.toString
