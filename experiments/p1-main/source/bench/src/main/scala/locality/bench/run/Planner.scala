package locality.bench.run

import locality.bench.data.*
import locality.bench.lang.*
import locality.bench.prompt.*
import locality.llm.*
import locality.llm.json.*
import JsonSupport.*
import java.nio.file.Path
import java.time.Instant
import java.util.Random

final case class Trial(id: String, expanded: ExpandedCase, replicate: Int, request: LlmRequest, metrics: PromptMetrics):
  def metadata: Obj = Json.obj("id" -> s(id), "familyId" -> s(expanded.family.id), "task" -> s(expanded.task.id),
    "view" -> s(expanded.view.map(_.id).getOrElse("complete")), "style" -> s(expanded.spec.style.id),
    "lexicalRegime" -> s(expanded.spec.lexicon.regime), "lexiconSetId" -> n(expanded.spec.lexicon.setId),
    "depth" -> n(expanded.family.peakDepth), "filler" -> n(expanded.family.fillerStatements),
    "replicate" -> n(replicate), "requestHash" -> s(hash(requestJson(request))), "metrics" -> Planner.metricsJson(metrics))
  def gold: Obj =
    val observation = expanded.goldObservation
    Json.obj("id" -> s(id), "value" -> n(observation.value), "stack" -> Arr(observation.activeScopes.map(s)), "source" -> s(expanded.goldSource))

object Planner:
  def build(p: Protocol): Vector[Trial] =
    if p.phase == "p1" && p.preset != "main" && p.preset != "smoke" then
      require(p.intVector("depths").forall(_ <= 8), "P1 AST-to-source depth must not exceed 8; use a separate reading suite for larger depths")
      require(p.intVector("fillersGeneration").forall(_ <= 32), "P1 AST-to-source filler must not exceed 32")
    def generated(task: Task): Vector[CaseFamily] =
      val fillers = p.intVector(if task == Task.AstToSource then "fillersGeneration" else "fillersReading")
      for
        depth <- p.intVector("depths")
        filler <- fillers
        i <- (0 until p.integer("seedsPerCell")).toVector
      yield Generator.generateFamily(GenerationConfig(depth, filler, i), Seeds.derive(p.seed, s"${p.preset}:${if task == Task.AstToSource then "generation" else "reading"}:$depth:$filler", i))
    val tasks = if p.phase == "p0" || p.preset == "main" then Vector(Task.ScopeLookup) else Task.values.toVector
    val blocks = tasks.flatMap { task =>
      val families = if p.preset == "smoke" then Generator.smokeFamilies else generated(task)
      DatasetAudit.audit(families).fold(errors => throw new IllegalArgumentException(errors.mkString("; ")), identity)
      families.zipWithIndex.flatMap { (family, i) =>
        val lexicons = if p.preset == "smoke" then Vector(Lexicon.natural) else
          Vector(Lexicon.natural, Generator.nonceLexicon(Seeds.derive(p.seed, s"lexicon:${family.peakDepth}:${family.fillerStatements}", 0), i % p.integer("seedsPerCell")))
        val views: Vector[Option[View]] = if task == Task.AstToSource then Vector(None)
          else if p.preset == "smoke" || p.preset == "main" then Vector(Some(View.AfterClose))
          else Vector(Some(View.BeforeClose), Some(View.AfterClose))
        for
          lexicon <- lexicons
          view <- views
          replicate <- (0 until p.integer("replicates")).toVector
        yield SyntaxStyle.values.toVector.map { style =>
          val c = ExpandedCase(family, task, view, RenderSpec(style, lexicon))
          val examples = Examples.forCase(c, p.seed, p.integer("fewShotCount"))
          val tokens = p.integer(if task == Task.AstToSource then "maxOutputTokensGeneration" else "maxOutputTokensReading")
          val request = PromptBuilder.build(c, examples, p.model, Some(str(p.config, "reasoningEffort")), tokens)
          val id = hash(Json.obj("protocolHash" -> s(p.protocolHash), "case" -> s(c.id), "replicate" -> n(replicate), "requestHash" -> s(hash(requestJson(request)))))
          Trial(id, c, replicate, request, PromptBuilder.describe(c, examples).metrics)
        }
      }
    }
    val random = new Random(Seeds.derive(p.seed, "scheduleSeed", 0))
    shuffle(blocks, random).flatMap(b => shuffle(b, random))
  private def shuffle[A](values: Vector[A], random: Random): Vector[A] =
    val buffer = scala.collection.mutable.ArrayBuffer.from(values)
    for i <- (buffer.length - 1) to 1 by -1 do
      val j = random.nextInt(i + 1)
      val tmp = buffer(i); buffer(i) = buffer(j); buffer(j) = tmp
    buffer.toVector
  def metricsJson(m: PromptMetrics): Obj =
    def offset(value: Option[SourceOffset]): JsonValue = value.map(o => Json.obj("line" -> n(o.line), "charOffset" -> n(o.charOffset), "byteOffset" -> n(o.byteOffset))).getOrElse(Null)
    Json.obj("grammarChars" -> n(m.grammarChars), "grammarBytes" -> n(m.grammarBytes), "examplesChars" -> n(m.examplesChars), "examplesBytes" -> n(m.examplesBytes),
      "testChars" -> n(m.testChars), "testBytes" -> n(m.testBytes), "sourceLines" -> n(m.sourceLines), "sourceChars" -> n(m.sourceChars), "sourceBytes" -> n(m.sourceBytes),
      "opener" -> offset(m.opener), "binding" -> offset(m.binding), "closeStart" -> offset(m.closeStart), "closeEnd" -> offset(m.closeEnd), "probe" -> offset(m.probe))
  def familyJson(f: CaseFamily): Obj =
    def cut(c: CutPoint): Obj = Json.obj("eventCount" -> n(c.eventCount), "probeId" -> s(c.probeId), "variable" -> s(c.variable))
    Json.obj("id" -> s(f.id), "program" -> s(Renderer.renderProgram(f.program, RenderSpec(SyntaxStyle.NamedEnd, Lexicon.natural))),
      "targetVariable" -> s(f.targetVariable), "before" -> cut(f.before), "after" -> cut(f.after), "peakDepth" -> n(f.peakDepth), "fillerStatements" -> n(f.fillerStatements),
      "closeCount" -> n(f.closeCount), "structureSeed" -> n(f.structureSeed), "identifierSeed" -> n(f.identifierSeed), "valueSeed" -> n(f.valueSeed), "fillerSeed" -> n(f.fillerSeed),
      "siblingNames" -> Arr(f.siblingNames.map(s)), "visibleOwner" -> opt(f.visibleOwner), "focusedName" -> s(f.focusedName))
  private def distributionJson(families: Vector[CaseFamily], cases: Vector[ExpandedCase]): Obj =
    val summary = DatasetAudit.distribution(families, cases).fold(errors => throw new IllegalArgumentException(errors.mkString("; ")), identity)
    def kinds(counts: Vector[KindCount]): Obj = Obj(counts.map(c => c.kind.natural -> n(c.count)))
    Json.obj("familyCount" -> n(summary.familyCount), "blockCount" -> n(summary.blockCount), "kindCounts" -> kinds(summary.kindCounts),
      "kindCells" -> Arr(summary.kindCells.map(c => Json.obj("depth" -> n(c.depth), "filler" -> n(c.fillerStatements), "families" -> n(c.familyCount), "counts" -> kinds(c.counts)))),
      "nonceCells" -> Arr(summary.nonceCells.map(c => Json.obj("task" -> s(c.task.id), "depth" -> n(c.depth), "filler" -> n(c.fillerStatements), "families" -> n(c.familyCount), "counts" -> Arr(c.counts.map(value => n(value)))))),
      "fixedNonceMappings" -> Arr(Lexicon.fixedNonce.map(l => Json.obj("setId" -> n(l.setId), "unit" -> s(l.unit), "func" -> s(l.func), "area" -> s(l.area), "let" -> s(l.let), "nop" -> s(l.nop), "end" -> s(l.end)))),
      "paddedEndSuffix" -> s("junk z_aaaaaaaa"), "kindExclusionThreshold" -> Null)
  def familyFrom(value: JsonValue): CaseFamily =
    def cut(v: JsonValue): CutPoint = CutPoint(num(v, "eventCount").toInt, str(v, "probeId"), str(v, "variable"))
    val program = Parser.parseProgram(str(value, "program"), RenderSpec(SyntaxStyle.NamedEnd, Lexicon.natural)).fold(e => throw new IllegalArgumentException(e.message), identity)
    CaseFamily(str(value, "id"), program, str(value, "targetVariable"), cut(field(value, "before")), cut(field(value, "after")), num(value, "peakDepth").toInt,
      num(value, "fillerStatements").toInt, num(value, "closeCount").toInt, num(value, "structureSeed"), num(value, "identifierSeed"), num(value, "valueSeed"), num(value, "fillerSeed"),
      arr(field(value, "siblingNames")).map(string), optionalString(value, "visibleOwner"), str(value, "focusedName"))
  def expandedFrom(meta: JsonValue, families: Map[String, CaseFamily]): ExpandedCase =
    val style = SyntaxStyle.values.find(_.id == str(meta, "style")).getOrElse(throw new IllegalArgumentException("Unknown style"))
    val lexical = if str(meta, "lexicalRegime") == "natural" then Lexicon.natural else Lexicon.fixedNonce(num(meta, "lexiconSetId").toInt)
    ExpandedCase(families(str(meta, "familyId")), Task.fromId(str(meta, "task")).get, View.fromId(str(meta, "view")), RenderSpec(style, lexical))
  def save(p: Protocol, out: Path): Unit =
    val store = new RunStore(out)
    try
      require(!store.exists("protocol.json"), "Output is already a run; choose a new directory")
      val trials = build(p)
      def lines(values: Vector[JsonValue]): String = values.map(Json.canonical).mkString("", "\n", "\n")
      store.atomic("protocol.json", Json.canonical(p.json))
      val families = trials.map(_.expanded.family).groupBy(_.id).values.map(_.head).toVector.sortBy(_.id)
      store.atomic("families.jsonl", lines(families.map(familyJson)))
      store.atomic("cases.jsonl", lines(trials.map(_.metadata)))
      store.atomic("gold.jsonl", lines(trials.map(_.gold)))
      store.atomic("dataset-audit.json", Json.canonical(distributionJson(families, trials.map(_.expanded))))
      store.atomic("schedule.jsonl", lines(trials.zipWithIndex.map((t, i) => Json.obj("index" -> n(i), "id" -> s(t.id)))))
      trials.foreach(t => store.atomic(s"trials/${t.id}/request.json", locality.llm.ResponsesRequestJson.generationBody(t.request)))
      store.atomic("capabilities.json", "{\"verified\":false}")
      store.atomic("manifest.json", Json.canonical(Json.obj("protocolHash" -> s(p.protocolHash), "datasetHash" -> s(sha(store.read("families.jsonl") + store.read("cases.jsonl") + store.read("gold.jsonl"))),
        "scheduleHash" -> s(sha(store.read("schedule.jsonl"))), "auditHash" -> s(sha(store.read("dataset-audit.json"))), "planned" -> n(trials.size), "familyCount" -> n(families.size), "createdAt" -> s(Instant.now.toString), "requestedModel" -> s(p.model), "synthetic_mock" -> Bool(false))))
      audit(store)
      println(s"Planned ${trials.size} requests, ${families.size} structural families; protocol=${p.protocolHash}")
    finally store.close()
  def audit(store: RunStore, verifySource: Boolean = true): Unit =
    val p = Protocol.from(parse(store.read("protocol.json")))
    if verifySource then require(str(p.config, "sourceHash") == Protocol.sourceHash, "Implementation changed; create a new plan")
    val manifest = parse(store.read("manifest.json"))
    require(str(manifest, "protocolHash") == p.protocolHash)
    require(str(manifest, "datasetHash") == sha(store.read("families.jsonl") + store.read("cases.jsonl") + store.read("gold.jsonl")), "Dataset hash mismatch")
    require(str(manifest, "scheduleHash") == sha(store.read("schedule.jsonl")), "Schedule hash mismatch")
    val families = store.jsonLines("families.jsonl").map(familyFrom)
    DatasetAudit.audit(families).fold(errors => throw new IllegalArgumentException(errors.mkString("; ")), identity)
    val byId = families.map(f => f.id -> f).toMap
    val cases = store.jsonLines("cases.jsonl")
    require(str(manifest, "auditHash") == sha(store.read("dataset-audit.json")), "Dataset audit hash mismatch")
    require(hash(parse(store.read("dataset-audit.json"))) == hash(distributionJson(families, cases.map(expandedFrom(_, byId)))), "Dataset distribution differs from saved audit")
    val gold = store.jsonLines("gold.jsonl").map(g => str(g, "id") -> g).toMap
    require(cases.size == num(manifest, "planned") && gold.size == cases.size, "Plan cardinality mismatch")
    cases.foreach { meta =>
      val id = str(meta, "id")
      val c = expandedFrom(meta, byId)
      val saved = parse(store.read(s"trials/$id/request.json"))
      require(hash(saved) == str(meta, "requestHash"), "Request hash mismatch")
      val rebuilt = PromptBuilder.build(c, Examples.forCase(c, p.seed, p.integer("fewShotCount")), p.model, Some(str(p.config, "reasoningEffort")), p.integer(if c.task == Task.AstToSource then "maxOutputTokensGeneration" else "maxOutputTokensReading"))
      require(hash(saved) == hash(requestJson(rebuilt)), "Prompt template changed")
      require(hash(Json.obj("protocolHash" -> s(p.protocolHash), "case" -> s(c.id), "replicate" -> field(meta, "replicate"), "requestHash" -> field(meta, "requestHash"))) == id, "Trial identity mismatch")
      require(c.goldObservation.value == num(gold(id), "value") && c.goldObservation.activeScopes == arr(field(gold(id), "stack")).map(string) && c.goldSource == str(gold(id), "source"), "Gold mismatch")
    }
    val schedule = store.jsonLines("schedule.jsonl").map(v => str(v, "id"))
    require(schedule.size == cases.size && schedule.toSet == cases.map(v => str(v, "id")).toSet, "Schedule is not an exact permutation")
