package locality.bench.run

import java.nio.file.{Files, Path}
import locality.llm.json.*
import JsonSupport.*

final case class Protocol(preset: String, phase: String, config: Obj, protocolHash: String):
  def model: String = str(config, "model")
  def integer(key: String): Int = Math.toIntExact(num(config, key))
  def seed: Long = num(config, "masterSeed")
  def intVector(key: String): Vector[Int] = arr(field(config, key)).map(v => Math.toIntExact(long(v)))
  def json: Obj = Json.obj("preset" -> s(preset), "phase" -> s(phase), "config" -> config, "protocolHash" -> s(protocolHash))

object Protocol:
  val allowed = Set("model", "reasoningEffort", "maxOutputTokensReading", "maxOutputTokensGeneration", "fewShotCount", "replicates", "masterSeed", "depths", "fillersReading", "fillersGeneration", "seedsPerCell", "concurrency", "maxRpm", "maxTpm", "bootstrapSeed")
  def load(preset: String, phase: String, configPath: Option[Path] = None, modelOverride: Option[String] = None): Protocol =
    require(Set("smoke", "pilot", "main").contains(preset), "Unknown preset")
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
