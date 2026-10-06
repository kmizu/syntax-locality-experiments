package locality.bench.lang

enum SyntaxStyle(val id: String):
  case Braces extends SyntaxStyle("braces")
  case GenericEnd extends SyntaxStyle("generic_end")
  case TypedEnd extends SyntaxStyle("typed_end")
  case NamedEnd extends SyntaxStyle("named_end")
  case PaddedEnd extends SyntaxStyle("padded_end")

object SyntaxStyle:
  def fromId(id: String): Option[SyntaxStyle] = values.find(_.id == id)

final case class RenderSpec(style: SyntaxStyle, lexicon: Lexicon, indentation: Boolean = false)
final case class ParseError(code: String, line: Int, column: Int, message: String)
