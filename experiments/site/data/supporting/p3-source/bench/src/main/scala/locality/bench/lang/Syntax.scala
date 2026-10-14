package locality.bench.lang

enum SyntaxStyle(val id: String):
  case Braces extends SyntaxStyle("braces")
  case GenericEnd extends SyntaxStyle("generic_end")
  case TypedEnd extends SyntaxStyle("typed_end")
  case NamedEnd extends SyntaxStyle("named_end")
  case PaddedEnd extends SyntaxStyle("padded_end")
  case DepthEnd extends SyntaxStyle("depth_end")
  case Indent extends SyntaxStyle("indent")

object SyntaxStyle:
  def fromId(id: String): Option[SyntaxStyle] = values.find(_.id == id)
  /** The original five paired conditions A–E; protocols without an explicit `styles` key expand exactly these. */
  val original: Vector[SyntaxStyle] = Vector(Braces, GenericEnd, TypedEnd, NamedEnd, PaddedEnd)

/** How an import names its target: the module name alone, or the full chain of module names from the top level. */
enum Qualification(val id: String):
  case Short extends Qualification("short")
  case Fq extends Qualification("fq")

object Qualification:
  def fromId(id: String): Option[Qualification] = values.find(_.id == id)

final case class RenderSpec(style: SyntaxStyle, lexicon: Lexicon, indentation: Boolean = false,
  qualification: Qualification = Qualification.Short)
final case class ParseError(code: String, line: Int, column: Int, message: String)
