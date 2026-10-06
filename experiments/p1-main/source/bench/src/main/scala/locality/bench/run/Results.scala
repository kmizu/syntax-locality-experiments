package locality.bench.run

import locality.bench.data.*
import locality.bench.lang.*
import locality.bench.score.*
import locality.bench.stats.{TokenUsage as ReportUsage, *}
import locality.bench.report.*
import locality.llm.json.*
import JsonSupport.*

object Results:
  def observations(store: RunStore): Vector[TrialObservation] =
    val families = store.jsonLines("families.jsonl").map(Planner.familyFrom).map(f => f.id -> f).toMap
    val gold = store.jsonLines("gold.jsonl").map(g => str(g, "id") -> g).toMap
    val events = store.jsonLines("events.jsonl")
    val dispatched = events.filter(e => optionalString(e, "kind").contains("dispatch")).map(e => str(e, "trialId")).toSet
    store.jsonLines("cases.jsonl").map { meta =>
      val id = str(meta, "id")
      val c = Planner.expandedFrom(meta, families)
      val terminal = store.terminal(id)
      val status = terminal.map(t => str(t, "status")).getOrElse(if dispatched(id) then "ambiguous_outcome" else "not_dispatched")
      val text = terminal.map(t => str(t, "text")).getOrElse("")
      val response = GradingResponse(status, text, terminal.exists(t => arr(field(t, "refusals")).nonEmpty))
      val g = gold(id)
      val score = c.task match
        case Task.ScopeLookup => LookupGrader.grade(num(g, "value").toInt, response)
        case Task.ActiveStack =>
          val known = Parser.parsePrefix(c.source, c.spec).toOption.toVector.flatMap(_.events).collect { case ParsedEvent.Open(_, name) => name }.toSet
          StackGrader.grade(arr(field(g, "stack")).map(string), response, known)
        case Task.AstToSource => GenerationGrader.grade(c.family.program, response, c.spec)
      val trialEvents = events.filter(e => optionalString(e, "trialId").contains(id))
      val dispatchAttempts = trialEvents.filter(e => optionalString(e, "kind").contains("dispatch")).map(e => num(e, "attempt").toInt)
      val generationAttempts = Math.max(terminal.map(t => num(t, "generationAttempts").toInt).getOrElse(0), dispatchAttempts.maxOption.getOrElse(0))
      val countEvents = trialEvents.filter(e => Set("token_count_attempt", "planning_token_count_attempt").contains(optionalString(e, "kind").getOrElse("")))
      val countAttempts = Math.max(terminal.map(t => num(t, "countAttempts").toInt).getOrElse(0), countEvents.size)
      val attemptUsage = (1 to generationAttempts).toVector.flatMap { i =>
        val path = f"trials/$id/attempt-$i%03d-meta.json"
        if store.exists(path) then reportUsage(field(parse(store.read(path)), "usage")) else None
      }
      val finalUsage = terminal.flatMap(t => reportUsage(field(t, "usage"))).orElse {
        val path = f"trials/$id/attempt-$generationAttempts%03d-meta.json"
        if generationAttempts > 0 && store.exists(path) then reportUsage(field(parse(store.read(path)), "usage")) else None
      }
      val req = requestFrom(parse(store.read(s"trials/$id/request.json")))
      val goldText = c.task match
        case Task.ScopeLookup => num(g, "value").toString
        case Task.ActiveStack => Json.render(field(g, "stack"))
        case Task.AstToSource => str(g, "source")
      TrialObservation(id, c.family.id, c.task.id, str(meta, "view"), c.spec.style.id, c.spec.lexicon.regime, c.family.peakDepth, c.family.fillerStatements,
        num(meta, "replicate").toInt, score, dispatched(id), terminal.nonEmpty, finalUsage, terminal.map(t => num(t, "durationMs")),
        countAttempts + generationAttempts, generationAttempts, attemptUsage,
        None, req.instructions + "\n" + req.input.map(_.content).mkString("\n"), text, goldText, id)
    }
  private def reportUsage(value: JsonValue): Option[ReportUsage] = value match
    case Null => None
    case _ =>
      def optional(key: String): Option[Long] = obj(value).get(key).filter(_ != Null).map(long)
      Some(ReportUsage(optional("inputTokens"), optional("outputTokens"), optional("reasoningTokens"), optional("cachedInputTokens")))
  def write(store: RunStore): Unit =
    Planner.audit(store)
    val p = Protocol.from(parse(store.read("protocol.json")))
    if store.exists("freeze.json") then require(Freeze.verify(store, p.protocolHash), "Frozen artifacts changed; refuse scoring under original protocol")
    val rows = observations(store)
    val manifest = parse(store.read("manifest.json"))
    val mock = if store.exists("execution-mode.json") then bool(field(parse(store.read("execution-mode.json")), "synthetic_mock")) else false
    val metadata = ReportMetadata(p.preset, p.phase, p.protocolHash, p.model, str(manifest, "createdAt"), mock,
      num(p.config, "bootstrapSeed"), rows.map(_.lexicalRegime).distinct.sorted, Json.canonical(p.config))
    Report.write(store.dir, metadata, rows)
    println(s"Report regenerated from saved data: ${store.dir.resolve("report.md")}; ${rows.count(_.terminal)}/${rows.size} terminal.")
