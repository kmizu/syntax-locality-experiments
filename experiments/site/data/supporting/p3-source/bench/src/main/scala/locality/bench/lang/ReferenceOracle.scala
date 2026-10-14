package locality.bench.lang

object ReferenceOracle:
  /** One lexical level: its own `let` bindings and the binding maps it imported, in source order. */
  private final case class Level(own: Map[String, Int], imported: Vector[Map[String, Int]]):
    // Own bindings first, then imports with the later import winning.
    def lookup(variable: String): Option[Int] =
      own.get(variable).orElse(imported.reverseIterator.flatMap(_.get(variable)).take(1).toVector.headOption)

  // Deliberately walks the AST recursively; no renderer, parser, or event evaluator.
  def observe(program: Program, cut: CutPoint): Either[OracleError, Observation] =
    def size(body: Vector[Stmt]): Int = body.map {
      case Stmt.Scope(_, _, children) => 2 + size(children)
      case _ => 1
    }.sum
    if cut.eventCount < 0 || cut.eventCount > size(program.body) then
      return Left(OracleError("cut_out_of_range", s"Invalid cut ${cut.eventCount}"))
    var visited = 0
    var answer: Option[Either[OracleError, Observation]] = None
    // Direct `let` bindings of every module whose body has been completely walked, keyed by its unique name.
    var closed = Map.empty[String, Map[String, Int]]
    def snapshot(env: Vector[Level], scopes: Vector[String]): Unit =
      if visited == cut.eventCount && answer.isEmpty then
        answer = Some(env.reverseIterator.flatMap(_.lookup(cut.variable)).take(1).toVector.headOption
          .map(v => Right(Observation(v, scopes)))
          .getOrElse(Left(OracleError("unbound_variable", s"Unbound ${cut.variable}"))))
    def walk(body: Vector[Stmt], inherited: Vector[Level], scopes: Vector[String]): Vector[Level] =
      var env = inherited
      snapshot(env, scopes)
      body.foreach { stmt =>
        if answer.isEmpty then stmt match
          case Stmt.Let(variable, value) =>
            if env.last.own.contains(variable) then answer = Some(Left(OracleError("duplicate_binding", variable)))
            else
              env = env.updated(env.size - 1, env.last.copy(own = env.last.own.updated(variable, value)))
              visited += 1
              snapshot(env, scopes)
          case Stmt.Nop(_) =>
            visited += 1
            snapshot(env, scopes)
          case Stmt.Import(target) =>
            if scopes.contains(target) then answer = Some(Left(OracleError("import_open_module", s"Import of open module $target")))
            else closed.get(target) match
              case None => answer = Some(Left(OracleError("import_unknown_module", s"Import of unknown or not yet closed module $target")))
              case Some(bindings) =>
                env = env.updated(env.size - 1, env.last.copy(imported = env.last.imported :+ bindings))
                visited += 1
                snapshot(env, scopes)
          case Stmt.Scope(_, name, children) =>
            visited += 1
            val childEnv = env :+ Level(Map.empty, Vector.empty)
            val childScopes = scopes :+ name
            snapshot(childEnv, childScopes)
            val finished = if answer.isEmpty then walk(children, childEnv, childScopes) else childEnv
            if answer.isEmpty then
              closed = closed.updated(name, finished.last.own)
              visited += 1
              snapshot(env, scopes)
      }
      env
    walk(program.body, Vector(Level(Map.empty, Vector.empty)), Vector.empty)
    answer.getOrElse(Left(OracleError("cut_out_of_range", "Traversal ended before cut")))
