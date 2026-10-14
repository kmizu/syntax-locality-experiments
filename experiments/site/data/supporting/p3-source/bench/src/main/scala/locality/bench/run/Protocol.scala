package locality.bench.run

import java.nio.file.{Files, Path}
import locality.bench.lang.{Qualification, SyntaxStyle}
import locality.llm.json.*
import JsonSupport.*

final case class Protocol(preset: String, phase: String, config: Obj, protocolHash: String):
  def model: String = str(config, "model")
  def integer(key: String): Int = Math.toIntExact(num(config, key))
  def seed: Long = num(config, "masterSeed")
  def intVector(key: String): Vector[Int] = arr(field(config, key)).map(v => Math.toIntExact(long(v)))
  /** Styles to expand, in enum order. Absent key: the original five conditions, so existing hashes and plans are unchanged. */
  def styles: Vector[SyntaxStyle] = config.get("styles") match
    case None => SyntaxStyle.original
    case Some(value) =>
      val ids = arr(value).map(string)
      val resolved = ids.map(id => SyntaxStyle.fromId(id).getOrElse(throw new IllegalArgumentException(s"Unknown style: $id")))
      SyntaxStyle.values.toVector.filter(resolved.contains)
  /** P3b import families; absent key: false, so existing hashes, case ids, and prompts are unchanged. */
  def imports: Boolean = config.get("imports").exists(bool)
  def tasks: Option[Vector[locality.bench.data.Task]] = config.get("tasks").map(v => arr(v).map(t => locality.bench.data.Task.fromId(string(t)).getOrElse(throw new IllegalArgumentException(s"Unknown task: ${string(t)}"))))
  /** Import qualification; absent key: short. It only affects rendering, ids, and prompts when `imports` is true. */
  def importQualification: Qualification = config.get("importQualification") match
    case None => Qualification.Short
    case Some(value) => Qualification.fromId(string(value)).getOrElse(throw new IllegalArgumentException(s"Unknown importQualification: ${string(value)}"))
  def json: Obj = Json.obj("preset" -> s(preset), "phase" -> s(phase), "config" -> config, "protocolHash" -> s(protocolHash))

object Protocol:
  val allowed = Set("model", "reasoningEffort", "maxOutputTokensReading", "maxOutputTokensGeneration", "fewShotCount", "replicates", "masterSeed", "depths", "fillersReading", "fillersGeneration", "seedsPerCell", "concurrency", "maxRpm", "maxTpm", "bootstrapSeed", "styles", "imports", "importQualification", "tasks")
  def load(preset: String, phase: String, configPath: Option[Path] = None, modelOverride: Option[String] = None): Protocol =
    require(Set("smoke", "pilot", "main", "p3").contains(preset), "Unknown preset")
    require(Set("p0", "p1").contains(phase), "Unknown phase")
    val raw = obj(parse(Files.readString(configPath.getOrElse(Path.of(s"configs/$preset.json")))))
    require(raw.fields.forall(f => allowed.contains(f._1)), "Unknown config key")
    val model = modelOverride.orElse(sys.env.get("OPENAI_MODEL")).getOrElse(optionalString(raw, "model").getOrElse("gpt-5.6-terra"))
    require(model.nonEmpty, "Model cannot be empty")
    val additions = Vector("model" -> s(model), "sourceHash" -> s(sourceHash), "generatorVersion" -> s("1"), "promptVersion" -> s("1"), "scorerVersion" -> s("1"), "bootstrapSeed" -> raw.get("bootstrapSeed").getOrElse(n(20261005L)))
    val config = Obj(raw.fields.filterNot(p => additions.exists(_._1 == p._1)) ++ additions)
    val protocol = Protocol(preset, phase, config, hash(Json.obj("preset" -> s(preset), "phase" -> s(phase), "config" -> config)))
    Vector("maxOutputTokensReading", "maxOutputTokensGeneration", "replicates", "seedsPerCell", "concurrency", "maxRpm", "maxTpm").foreach(k => require(protocol.integer(k) > 0, s"$k must be positive"))
    require(protocol.integer("fewShotCount") >= 0)
    require(protocol.intVector("depths").nonEmpty && protocol.intVector("depths").forall(d => d >= 2 && d <= 64))
    Vector("fillersReading", "fillersGeneration").foreach(k => require(protocol.intVector(k).nonEmpty && protocol.intVector(k).forall(f => f >= 0)))
    Vector("depths", "fillersReading", "fillersGeneration").foreach(k => require(protocol.intVector(k).distinct.size == protocol.intVector(k).size, s"$k must not contain duplicate grid cells"))
    require(Set("none", "low", "medium", "high", "xhigh", "max").contains(str(config, "reasoningEffort")))
    require(protocol.integer("concurrency") <= 32)
    raw.get("styles").foreach { value =>
      val ids = arr(value).map(string)
      require(ids.nonEmpty && ids.distinct.size == ids.size, "styles must be a non-empty array of distinct style ids")
      require(ids.forall(id => SyntaxStyle.fromId(id).nonEmpty), s"Unknown style in styles: ${ids.filterNot(id => SyntaxStyle.fromId(id).nonEmpty).mkString(", ")}")
    }
    raw.get("imports").foreach(value => require(value.isInstanceOf[Bool], "imports must be a boolean"))
    raw.get("importQualification").foreach(value => require(value.isInstanceOf[Str] && Qualification.fromId(string(value)).nonEmpty, "importQualification must be \"short\" or \"fq\""))
    raw.get("tasks").foreach { value =>
      require(value.isInstanceOf[Arr] && arr(value).nonEmpty && arr(value).forall(_.isInstanceOf[Str]), "tasks must be a non-empty array of task ids")
      require(protocol.tasks.get.distinct.size == protocol.tasks.get.size, "tasks must not repeat")
    }
    require(preset == "p3" || raw.get("tasks").isEmpty, "tasks selection is only allowed in the p3 preset")
    require(!(protocol.imports && preset == "smoke"), "smoke uses fixed hand-written families without imports; import plans need pilot or main")
    protocol
  def from(value: JsonValue): Protocol =
    val p = Protocol(str(value, "preset"), str(value, "phase"), obj(field(value, "config")), str(value, "protocolHash"))
    require(hash(Json.obj("preset" -> s(p.preset), "phase" -> s(p.phase), "config" -> p.config)) == p.protocolHash, "Protocol hash mismatch")
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
