package locality.bench.lang

object Renderer:
  /** One parsed event; `text` is None for events that produce no source line (indent-style closes). */
  private final case class Line(text: Option[String], depthAfter: Int)

  def renderProgram(program: Program, spec: RenderSpec): String = lines(program, spec).flatMap(_.text).mkString("", "\n", "\n")
  def renderPrefix(program: Program, cut: CutPoint, spec: RenderSpec): String =
    val events = lines(program, spec)
    require(cut.eventCount >= 0 && cut.eventCount <= events.size, "cut out of range")
    val taken = events.take(cut.eventCount)
    val depth = taken.lastOption.map(_.depthAfter).getOrElse(0)
    val probe = indent(depth, spec) + s"probe ${cut.probeId} ${cut.variable}"
    (taken.flatMap(_.text) :+ probe).mkString("", "\n", "\n")

  /** Every block name mapped to its chain of enclosing block names plus itself, from the top level inward. */
  def ancestorChains(program: Program): Map[String, Vector[String]] =
    def walk(body: Vector[Stmt], chain: Vector[String]): Map[String, Vector[String]] = body.flatMap {
      case Stmt.Scope(_, name, children) => walk(children, chain :+ name) + (name -> (chain :+ name))
      case _ => Map.empty[String, Vector[String]]
    }.toMap
    walk(program.body, Vector.empty)

  private def indent(depth: Int, spec: RenderSpec): String =
    if spec.indentation || spec.style == SyntaxStyle.Indent then "  " * depth else ""
  private def lines(program: Program, spec: RenderSpec): Vector[Line] =
    val out = Vector.newBuilder[Line]
    lazy val chains = ancestorChains(program)
    def importPath(target: String): String = spec.qualification match
      case Qualification.Short => target
      case Qualification.Fq =>
        chains.getOrElse(target, throw new IllegalArgumentException(s"Import target $target is not a block of the program")).mkString(".")
    def emit(body: Vector[Stmt], depth: Int): Unit = body.foreach {
      case Stmt.Let(variable, value) => out += Line(Some(indent(depth, spec) + s"${spec.lexicon.let} $variable $value"), depth)
      case Stmt.Nop(payload) => out += Line(Some(indent(depth, spec) + s"${spec.lexicon.nop} $payload"), depth)
      case Stmt.Import(target) => out += Line(Some(indent(depth, spec) + s"${spec.lexicon.importWord} ${importPath(target)}"), depth)
      case Stmt.Scope(kind, name, children) =>
        val opening = s"${spec.lexicon.keyword(kind)} $name" + (if spec.style == SyntaxStyle.Braces then " {" else "")
        out += Line(Some(indent(depth, spec) + opening), depth + 1)
        emit(children, depth + 1)
        val closing = spec.style match
          case SyntaxStyle.Braces => Some("}")
          case SyntaxStyle.GenericEnd => Some(spec.lexicon.end)
          case SyntaxStyle.TypedEnd => Some(s"${spec.lexicon.end} ${spec.lexicon.keyword(kind)}")
          case SyntaxStyle.NamedEnd => Some(s"${spec.lexicon.end} ${spec.lexicon.keyword(kind)} $name")
          case SyntaxStyle.PaddedEnd => Some(s"${spec.lexicon.end} junk z_aaaaaaaa")
          case SyntaxStyle.DepthEnd => Some(s"${spec.lexicon.end} ${depth + 1}")
          case SyntaxStyle.Indent => None
        out += Line(closing.map(indent(depth, spec) + _), depth)
    }
    emit(program.body, 0)
    out.result()
