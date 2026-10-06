package locality.bench.run

import java.nio.file.{Files, Path}
import locality.llm.json.*
import locality.bench.data.Task
import JsonSupport.*

final case class Protocol(preset: String, phase: String, config: Obj, protocolHash: String):
  def model: String = str(config, "model")
  def integer(key: String): Int = Math.toIntExact(num(config, key))
  def seed: Long = num(config, "masterSeed")
  def intVector(key: String): Vector[Int] = arr(field(config, key)).map(v => Math.toIntExact(long(v)))
  def tasks: Vector[Task] = Protocol.selectedTasks(preset, phase, config)
  def json: Obj = Json.obj("preset" -> s(preset), "phase" -> s(phase), "config" -> config, "protocolHash" -> s(protocolHash))

object Protocol:
  val allowed = Set("model", "reasoningEffort", "maxOutputTokensReading", "maxOutputTokensGeneration", "fewShotCount", "replicates", "masterSeed", "depths", "fillersReading", "fillersGeneration", "seedsPerCell", "concurrency", "maxRpm", "maxTpm", "bootstrapSeed", "tasks")
  private def selectedTasks(preset: String, phase: String, config: Obj): Vector[Task] =
    require(Set("smoke", "pilot", "main").contains(preset), "Unknown preset")
    require(Set("p0", "p1", "p2").contains(phase), "Unknown phase")
    require(!(preset == "main" && phase == "p2"), "P2 supports exploratory smoke/pilot only; no independently declared P2 main")
    val explicit = config.get("tasks").map { value =>
      val ids = arr(value).map(string)
      require(ids.nonEmpty && ids.distinct.size == ids.size, "Tasks must be nonempty and contain no duplicate IDs")
      ids.map(id => Task.fromId(id).getOrElse(throw new IllegalArgumentException(s"Unknown task ID: $id")))
    }
    if phase == "p2" then
      val selected = explicit.getOrElse(Vector(Task.AstToSource, Task.SourceToAst))
      require(selected.forall(_.isFullProgram), "P2 supports only ast_to_source and source_to_ast")
      selected
    else
      require(explicit.isEmpty, "Explicit tasks are supported only in P2; P0/P1 tasks are fixed")
      if phase == "p0" || preset == "main" then Vector(Task.ScopeLookup)
      else Vector(Task.ScopeLookup, Task.ActiveStack, Task.AstToSource)
  def load(preset: String, phase: String, configPath: Option[Path] = None, modelOverride: Option[String] = None): Protocol =
    require(Set("smoke", "pilot", "main").contains(preset), "Unknown preset")
    require(Set("p0", "p1", "p2").contains(phase), "Unknown phase")
    val raw = obj(parse(Files.readString(configPath.getOrElse(Path.of(s"configs/$preset.json")))))
    require(raw.fields.forall(f => allowed.contains(f._1)), "Unknown config key")
    val model = modelOverride.orElse(sys.env.get("OPENAI_MODEL")).getOrElse(optionalString(raw, "model").getOrElse("gpt-5.6-terra"))
    require(model.nonEmpty, "Model cannot be empty")
    val additions = Vector("model" -> s(model), "sourceHash" -> s(sourceHash), "generatorVersion" -> s("1"), "promptVersion" -> s(if phase == "p2" then "2" else "1"), "scorerVersion" -> s(if phase == "p2" then "2" else "1"), "bootstrapSeed" -> raw.get("bootstrapSeed").getOrElse(n(20261005L)))
    val config = Obj(raw.fields.filterNot(p => additions.exists(_._1 == p._1)) ++ additions)
    val protocol = Protocol(preset, phase, config, hash(Json.obj("preset" -> s(preset), "phase" -> s(phase), "config" -> config)))
    Vector("maxOutputTokensReading", "maxOutputTokensGeneration", "replicates", "seedsPerCell", "concurrency", "maxRpm", "maxTpm").foreach(k => require(protocol.integer(k) > 0, s"$k must be positive"))
    require(protocol.integer("fewShotCount") >= 0)
    require(protocol.intVector("depths").nonEmpty && protocol.intVector("depths").forall(d => d >= 2 && d <= 64))
    Vector("fillersReading", "fillersGeneration").foreach(k => require(protocol.intVector(k).nonEmpty && protocol.intVector(k).forall(f => f >= 0)))
    Vector("depths", "fillersReading", "fillersGeneration").foreach(k => require(protocol.intVector(k).distinct.size == protocol.intVector(k).size, s"$k must not contain duplicate grid cells"))
    require(Set("none", "low", "medium", "high", "xhigh", "max").contains(str(config, "reasoningEffort")))
    require(protocol.integer("concurrency") <= 32)
    protocol.tasks
    protocol
  def from(value: JsonValue): Protocol =
    val p = Protocol(str(value, "preset"), str(value, "phase"), obj(field(value, "config")), str(value, "protocolHash"))
    require(hash(Json.obj("preset" -> s(p.preset), "phase" -> s(p.phase), "config" -> p.config)) == p.protocolHash, "Protocol hash mismatch")
    p.tasks
    p
  def sourceHash: String =
    import scala.jdk.CollectionConverters.*
    val paths = Vector("bench/src/main", "llm-core/src/main").flatMap { dir =>
      if !Files.exists(Path.of(dir)) then Vector.empty
      else
        val walk = Files.walk(Path.of(dir))
        try walk.iterator().asScala.filter(p => Files.isRegularFile(p) && p.toString.endsWith(".scala")).toVector
        finally walk.close()
    } ++ Vector(Path.of("build.sbt"), Path.of("project/build.properties"))
    sha(paths.sortBy(_.toString).map(p => p.toString.replace('\\', '/') + "\n" + sha(Files.readString(p))).mkString("\n"))
