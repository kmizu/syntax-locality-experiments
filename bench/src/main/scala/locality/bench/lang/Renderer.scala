package locality.bench.lang

object Renderer:
  def renderProgram(program: Program, spec: RenderSpec): String = lines(program, spec).map(_._1).mkString("", "\n", "\n")
  def renderPrefix(program: Program, cut: CutPoint, spec: RenderSpec): String =
    val events = lines(program, spec)
    require(cut.eventCount >= 0 && cut.eventCount <= events.size, "cut out of range")
    val depth = events.take(cut.eventCount).lastOption.map(_._2).getOrElse(0)
    val probe = indent(depth, spec) + s"probe ${cut.probeId} ${cut.variable}"
    (events.take(cut.eventCount).map(_._1) :+ probe).mkString("", "\n", "\n")

  private def indent(depth: Int, spec: RenderSpec): String = if spec.indentation then "  " * depth else ""
  private def lines(program: Program, spec: RenderSpec): Vector[(String, Int)] =
    val out = Vector.newBuilder[(String, Int)]
    def emit(body: Vector[Stmt], depth: Int): Unit = body.foreach {
      case Stmt.Let(variable, value) => out += ((indent(depth, spec) + s"${spec.lexicon.let} $variable $value", depth))
      case Stmt.Nop(payload) => out += ((indent(depth, spec) + s"${spec.lexicon.nop} $payload", depth))
      case Stmt.Scope(kind, name, children) =>
        val opening = s"${spec.lexicon.keyword(kind)} $name" + (if spec.style == SyntaxStyle.Braces then " {" else "")
        out += ((indent(depth, spec) + opening, depth + 1))
        emit(children, depth + 1)
        val closing = spec.style match
          case SyntaxStyle.Braces => "}"
          case SyntaxStyle.GenericEnd => spec.lexicon.end
          case SyntaxStyle.TypedEnd => s"${spec.lexicon.end} ${spec.lexicon.keyword(kind)}"
          case SyntaxStyle.NamedEnd => s"${spec.lexicon.end} ${spec.lexicon.keyword(kind)} $name"
          case SyntaxStyle.PaddedEnd => s"${spec.lexicon.end} junk z_aaaaaaaa"
        out += ((indent(depth, spec) + closing, depth))
    }
    emit(program.body, 0)
    out.result()
