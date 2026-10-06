package locality.bench.lang

import scala.util.boundary
import boundary.break

object Parser:
  private final case class Frame(kind: Option[Kind], name: String, body: Vector[Stmt], bound: Set[String])
  private final case class Result(program: Program, prefix: Option[ParsedPrefix])
  def parseProgram(source: String, spec: RenderSpec): Either[ParseError, Program] =
    parse(source, spec, false).map(_.program)
  def parsePrefix(source: String, spec: RenderSpec): Either[ParseError, ParsedPrefix] =
    parse(source, spec, true).flatMap(_.prefix.toRight(ParseError("missing_probe", 1, 1, "Prefix requires a final probe")))

  private def parse(source: String, spec: RenderSpec, prefixMode: Boolean): Either[ParseError, Result] = boundary:
    var frames = Vector(Frame(None, "", Vector.empty, Set.empty))
    var events = Vector.empty[ParsedEvent]
    var probe: Option[ParsedPrefix] = None
    var usedNames = Set.empty[String]
    val sourceLines = source.split("\n", -1).toVector
    def failure(code: String, line: Int, message: String) = Left(ParseError(code, line, 1, message))
    for (raw, index) <- sourceLines.zipWithIndex do
      val line = raw.stripSuffix("\r").trim
      val number = index + 1
      if line.nonEmpty then
        if probe.nonEmpty then break(failure("after_probe", number, "Statements after probe are forbidden"))
        val words = line.split("[ \\t]+", -1).toVector
        val first = words.head
        if first == "probe" then
          if !prefixMode then break(failure("unexpected_probe", number, "Full programs cannot contain probe"))
          if words.size != 3 || !words(1).matches("q_[a-z]{8}") || !words(2).matches("x_[a-f]") then
            break(failure("invalid_probe", number, "Invalid probe"))
          probe = Some(ParsedPrefix(events, words(1), words(2)))
        else if first == spec.lexicon.let then
          if words.size != 3 || !words(1).matches("x_[a-f]") || !words(2).matches("[1-9][0-9]{3}") then
            break(failure("invalid_let", number, "Let requires variable and four digit value"))
          if frames.last.bound.contains(words(1)) then break(failure("duplicate_binding", number, words(1)))
          val stmt = Stmt.Let(words(1), words(2).toInt)
          frames = frames.updated(frames.size - 1, frames.last.copy(body = frames.last.body :+ stmt, bound = frames.last.bound + words(1)))
          events :+= ParsedEvent.Let(words(1), words(2).toInt)
        else if first == spec.lexicon.nop then
          if words.size != 2 || !words(1).matches("p_[a-z]{8}") then break(failure("invalid_nop", number, "Invalid nop"))
          frames = frames.updated(frames.size - 1, frames.last.copy(body = frames.last.body :+ Stmt.Nop(words(1))))
          events :+= ParsedEvent.Nop(words(1))
        else if spec.lexicon.kind(first).nonEmpty then
          val expectedSize = if spec.style == SyntaxStyle.Braces then 3 else 2
          if words.size != expectedSize || !words(1).matches("n_[a-z]{8}") || (expectedSize == 3 && words(2) != "{") then
            break(failure("invalid_open", number, "Invalid block opener"))
          if usedNames.contains(words(1)) then break(failure("duplicate_name", number, words(1)))
          usedNames += words(1)
          val kind = spec.lexicon.kind(first).get
          frames :+= Frame(Some(kind), words(1), Vector.empty, Set.empty)
          events :+= ParsedEvent.Open(kind, words(1))
        else if first == "}" || first == spec.lexicon.end then
          if frames.size == 1 then break(failure("underflow", number, "Close in global scope"))
          val closing = frames.last
          val expected = spec.style match
            case SyntaxStyle.Braces => Vector("}")
            case SyntaxStyle.GenericEnd => Vector(spec.lexicon.end)
            case SyntaxStyle.TypedEnd => Vector(spec.lexicon.end, spec.lexicon.keyword(closing.kind.get))
            case SyntaxStyle.NamedEnd => Vector(spec.lexicon.end, spec.lexicon.keyword(closing.kind.get), closing.name)
            case SyntaxStyle.PaddedEnd => Vector(spec.lexicon.end, "junk", "z_aaaaaaaa")
          if words != expected then
            val labelled = spec.style == SyntaxStyle.TypedEnd || spec.style == SyntaxStyle.NamedEnd
            val wellFormed = labelled && first == spec.lexicon.end && words.size == expected.size &&
              spec.lexicon.kind(words(1)).nonEmpty &&
              (spec.style != SyntaxStyle.NamedEnd || words(2).matches("n_[a-z]{8}"))
            val code =
              if wellFormed && words(1) != expected(1) then "close_kind_mismatch"
              else if wellFormed && spec.style == SyntaxStyle.NamedEnd && words(2) != closing.name then "close_name_mismatch"
              else "close_mismatch"
            break(failure(code, number, s"Expected ${expected.mkString(" ")}"))
          frames = frames.dropRight(1)
          frames = frames.updated(frames.size - 1, frames.last.copy(body = frames.last.body :+ Stmt.Scope(closing.kind.get, closing.name, closing.body)))
          events :+= ParsedEvent.Close
        else break(failure("unknown_statement", number, "Unknown or unsupported statement"))
    if !prefixMode && frames.size != 1 then failure("unclosed_scope", sourceLines.size, "Full program ends with open scopes")
    else if prefixMode && probe.isEmpty then failure("missing_probe", sourceLines.size, "Prefix requires a final probe")
    else Right(Result(Program(frames.head.body), probe))
