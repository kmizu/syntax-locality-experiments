package locality.bench.data

import locality.bench.lang.*

final case class KindCount(kind: Kind, count: Long)
final case class KindCellDistribution(depth: Int, fillerStatements: Int, familyCount: Int, counts: Vector[KindCount])
final case class NonceCellDistribution(task: Task, depth: Int, fillerStatements: Int, familyCount: Int, counts: Vector[Int])
final case class DatasetDistribution(familyCount: Int, blockCount: Long, kindCounts: Vector[KindCount],
  kindCells: Vector[KindCellDistribution], nonceCells: Vector[NonceCellDistribution])

object DatasetAudit:
  val version = "audit-v1"
  def distribution(families: Vector[CaseFamily], cases: Vector[ExpandedCase]): Either[Vector[String], DatasetDistribution] =
    val errors = scala.collection.mutable.Set.empty[String]
    val byId = families.map(f => f.id -> f).toMap
    if byId.size != families.size then errors += "duplicate family IDs in distribution audit"
    cases.foreach { c =>
      if !byId.get(c.family.id).contains(c.family) then errors += s"${c.family.id}: case differs from canonical family"
      val lexicon = c.spec.lexicon
      lexicon.regime match
        case "natural" =>
          if lexicon != Lexicon.natural then errors += s"${c.family.id}: natural keyword mapping differs from fixed lexicon"
        case "nonce" =>
          if lexicon.setId < 0 || lexicon.setId >= Lexicon.fixedNonce.size || Lexicon.fixedNonce(lexicon.setId) != lexicon then
            errors += s"${c.family.id}: nonce ID does not identify its fixed keyword mapping"
        case other => errors += s"${c.family.id}: unsupported lexical regime $other"
    }
    val nonce = cases.filter(_.spec.lexicon.regime == "nonce")
    nonce.groupBy(_.family.id).foreach { (id, variants) =>
      if variants.map(_.spec.lexicon.setId).distinct.size != 1 then errors += s"$id: nonce assignment differs between conditions"
    }
    val nonceCells = nonce.groupBy(c => (c.task, c.family.peakDepth, c.family.fillerStatements)).toVector
      .sortBy { case ((task, depth, filler), _) => (task.id, depth, filler) }.map { case ((task, depth, filler), variants) =>
        // Styles, views, and model replicates share an assignment; each family counts once.
        val assignments = variants.groupBy(_.family.id).values.map(_.head.spec.lexicon.setId).toVector
        val counts = Lexicon.fixedNonce.indices.map(id => assignments.count(_ == id)).toVector
        if counts.max - counts.min > 1 then errors += s"${task.id}/$depth/$filler: nonce allocation is not balanced across all eight sets"
        NonceCellDistribution(task, depth, filler, assignments.size, counts)
      }
    def blockKinds(body: Vector[Stmt]): Vector[Kind] = body.flatMap {
      case Stmt.Scope(kind, _, children) => kind +: blockKinds(children)
      case _ => Vector.empty
    }
    def counts(group: Vector[CaseFamily]): Vector[KindCount] =
      val kinds = group.flatMap(f => blockKinds(f.program.body))
      Kind.values.toVector.map(kind => KindCount(kind, kinds.count(_ == kind).toLong))
    val unique = byId.values.toVector.sortBy(_.id)
    val overall = counts(unique)
    val kindCells = unique.groupBy(f => (f.peakDepth, f.fillerStatements)).toVector.sortBy(_._1)
      .map { case ((depth, filler), group) => KindCellDistribution(depth, filler, group.size, counts(group)) }
    // The specification requests a distribution audit, not a post-hoc uniformity cutoff.
    val summary = DatasetDistribution(unique.size, overall.map(_.count).sum, overall, kindCells, nonceCells)
    if errors.nonEmpty then Left(errors.toVector.sorted) else Right(summary)

  def audit(families: Vector[CaseFamily]): Either[Vector[String], Unit] =
    val errors = Vector.newBuilder[String]
    if families.map(_.id).distinct.size != families.size then errors += "duplicate family IDs"
    for family <- families do
      val before = ReferenceOracle.observe(family.program, family.before)
      val after = ReferenceOracle.observe(family.program, family.after)
      if before.isLeft || after.isLeft then errors += s"${family.id}: reference failure"
      else
        if before.toOption.get.value == after.toOption.get.value then errors += s"${family.id}: same before/after value"
        if (before.toOption.get.activeScopes ++ after.toOption.get.activeScopes).exists(family.siblingNames.contains) then errors += s"${family.id}: live decoy sibling"
        def bindings(body: Vector[Stmt]): Map[String, Set[String]] = body.flatMap {
          case Stmt.Scope(_, name, children) => bindings(children) + (name -> children.collect { case Stmt.Let(v, _) => v }.toSet)
          case _ => Map.empty[String, Set[String]]
        }.toMap
        val inScopes = bindings(family.program.body)
        val owner = after.toOption.get.activeScopes.reverse.find(n => inScopes.getOrElse(n, Set.empty).contains(family.targetVariable))
        if owner != family.visibleOwner then errors += s"${family.id}: visible binding owner metadata mismatch"
        if before.toOption.get.activeScopes.lastOption != Some(family.focusedName) then errors += s"${family.id}: focused opener metadata mismatch"
      val es = Generator.events(family.program)
      val expectedCloses = math.max(1, math.min(family.peakDepth - 1, (family.peakDepth + 1) / 2))
      if family.closeCount != expectedCloses || family.after.eventCount - family.before.eventCount != family.closeCount then
        errors += s"${family.id}: close-count metadata mismatch"
      val closeSpan = es.slice(family.before.eventCount, family.after.eventCount)
      if closeSpan.size != family.closeCount || !closeSpan.forall(_ == ParsedEvent.Close) then errors += s"${family.id}: cut span is not the prescribed close sequence"
      val focus = es.lastIndexWhere { case ParsedEvent.Open(_, n) => n == family.focusedName; case _ => false }
      val filler = es.slice(focus + 1, family.before.eventCount).count { case ParsedEvent.Nop(_) => true; case _ => false }
      if focus < 0 || filler != family.fillerStatements then errors += s"${family.id}: filler metadata mismatch"
      if family.siblingNames.size != 2 || family.siblingNames.distinct.size != 2 || !family.siblingNames.forall(Generator.names(family.program).contains) then errors += s"${family.id}: sibling metadata mismatch"
      if before.toOption.exists(_.activeScopes.size != family.peakDepth) || after.toOption.exists(_.activeScopes.size != family.peakDepth - family.closeCount) then errors += s"${family.id}: cut-depth metadata mismatch"
      var depth = 0; var peak = 0
      es.foreach {
        case ParsedEvent.Open(_, _) => depth += 1; peak = math.max(peak, depth)
        case ParsedEvent.Close => depth -= 1
        case _ => ()
      }
      if peak != family.peakDepth || depth != 0 then errors += s"${family.id}: depth metadata mismatch"
      val values = es.collect { case ParsedEvent.Let(_, n) => n }
      if values.distinct.size != values.size || values.exists(n => n < 1000 || n > 9999) then errors += s"${family.id}: invalid values"
      for lexicon <- Lexicon.natural +: Lexicon.fixedNonce; style <- SyntaxStyle.values do
        val spec = RenderSpec(style, lexicon)
        if Parser.parseProgram(Renderer.renderProgram(family.program, spec), spec) != Right(family.program) then errors += s"${family.id}: round trip failure $style/${lexicon.setId}"
        for cut <- Vector(family.before, family.after) do
          val result = Parser.parsePrefix(Renderer.renderPrefix(family.program, cut, spec), spec).left.map(e => OracleError(e.code, e.message)).flatMap(EventOracle.observe)
          if result != ReferenceOracle.observe(family.program, cut) then errors += s"${family.id}: independent oracle mismatch $style/${lexicon.setId}"
    val result = errors.result()
    if result.isEmpty then Right(()) else Left(result)
