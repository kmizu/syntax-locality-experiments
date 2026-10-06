package locality.bench.lang

object ReferenceOracle:
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
    def snapshot(env: Vector[Map[String, Int]], scopes: Vector[String]): Unit =
      if visited == cut.eventCount && answer.isEmpty then
        answer = Some(env.reverseIterator.flatMap(_.get(cut.variable)).take(1).toVector.headOption
          .map(v => Right(Observation(v, scopes)))
          .getOrElse(Left(OracleError("unbound_variable", s"Unbound ${cut.variable}"))))
    def walk(body: Vector[Stmt], inherited: Vector[Map[String, Int]], scopes: Vector[String]): Vector[Map[String, Int]] =
      var env = inherited
      snapshot(env, scopes)
      body.foreach { stmt =>
        if answer.isEmpty then stmt match
          case Stmt.Let(variable, value) =>
            if env.last.contains(variable) then answer = Some(Left(OracleError("duplicate_binding", variable)))
            else
              env = env.updated(env.size - 1, env.last.updated(variable, value))
              visited += 1
              snapshot(env, scopes)
          case Stmt.Nop(_) =>
            visited += 1
            snapshot(env, scopes)
          case Stmt.Scope(_, name, children) =>
            visited += 1
            val childEnv = env :+ Map.empty[String, Int]
            val childScopes = scopes :+ name
            snapshot(childEnv, childScopes)
            if answer.isEmpty then walk(children, childEnv, childScopes)
            if answer.isEmpty then
              visited += 1
              snapshot(env, scopes)
      }
      env
    walk(program.body, Vector(Map.empty), Vector.empty)
    answer.getOrElse(Left(OracleError("cut_out_of_range", "Traversal ended before cut")))
