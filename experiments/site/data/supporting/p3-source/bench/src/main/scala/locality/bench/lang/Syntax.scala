package locality.bench.lang

enum SyntaxStyle(val id: String):
  case Braces extends SyntaxStyle("braces")
  case GenericEnd extends SyntaxStyle("generic_end")
  case TypedEnd extends SyntaxStyle("typed_end")
  case NamedEnd extends SyntaxStyle("named_end")
  case PaddedEnd extends SyntaxStyle("padded_end")
  case DepthEnd extends SyntaxStyle("depth_end")
  case Indent extends SyntaxStyle("indent")
  case IndentNamedEnd extends SyntaxStyle("indent_named_end")
  case IndentGenericEnd extends SyntaxStyle("indent_generic_end")

  /** Leading whitespace is structure: every body line sits exactly two spaces deeper than its opener. */
  def indented: Boolean = this == Indent || this == IndentNamedEnd || this == IndentGenericEnd
  /** Every block ends with an explicit closing line; only the pure indent style closes blocks by dedent. */
  def closingLine: Boolean = this != Indent
  /** The closing line repeats the opener's kind keyword and name (`named_end` and its indented variant). */
  def namedClose: Boolean = this == NamedEnd || this == IndentNamedEnd

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
