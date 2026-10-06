package locality.bench.run

import java.nio.file.{Path, Files}
import locality.llm.json.*
import JsonSupport.*
import java.time.Instant

object Freeze:
  private def artifacts(store: RunStore): Obj =
    import scala.jdk.CollectionConverters.*
    val base = Vector("protocol.json", "manifest.json", "families.jsonl", "cases.jsonl", "gold.jsonl", "dataset-audit.json", "schedule.jsonl", "capabilities.json", "preregistration.md", "dataset-manifest.json", "prompt-template-hashes.json", "provenance.json")
    val walk = Files.walk(store.dir.resolve("trials"))
    val requests = try walk.iterator().asScala.filter(p => Files.isRegularFile(p) && p.getFileName.toString == "request.json").toVector.map(store.dir.relativize(_).toString.replace('\\', '/')).sorted
      finally walk.close()
    Obj((base ++ requests).filter(store.exists).map(name => name -> s(sha(store.read(name)))))
  def verify(store: RunStore, protocolHash: String): Boolean =
    if !store.exists("freeze.json") then false
    else
      val frozen = parse(store.read("freeze.json"))
      val protocol = Protocol.from(parse(store.read("protocol.json")))
      str(frozen, "protocolHash") == protocolHash && protocol.protocolHash == protocolHash &&
        str(protocol.config, "sourceHash") == Protocol.sourceHash && hash(field(frozen, "artifacts")) == hash(artifacts(store))
  def create(store: RunStore, capabilities: Path): String =
    require(!store.exists("freeze.json"), "Already frozen")
    Planner.audit(store)
    val p = Protocol.from(parse(store.read("protocol.json")))
    require(p.phase != "p2", "P2 is exploratory smoke/pilot; a separate confirmatory plan is required")
    require(p.preset == "main", "Only main plans are frozen")
    require(str(p.config, "sourceHash") == Protocol.sourceHash, "Implementation changed")
    val probe = parse(Files.readString(capabilities))
    require(str(probe, "model") == p.model && str(probe, "reasoningEffort") == str(p.config, "reasoningEffort"), "Capability settings mismatch")
    require(num(probe, "maxOutputTokensReading") == p.integer("maxOutputTokensReading"), "Capability output-token setting mismatch")
    require(bool(field(probe, "tokenCountSupported")) && bool(field(probe, "generationSupported")), "Preflight must pass")
    require(bool(field(probe, "verified")) && !bool(field(probe, "synthetic_mock")), "Freeze requires verified real API capabilities")
    require(str(probe, "generationResponseId").nonEmpty && str(probe, "endpoint") == "https://api.openai.com/v1", "Missing preflight response provenance")
    store.atomic("capabilities.json", Json.canonical(probe))
    store.atomic("dataset-manifest.json", store.read("manifest.json"))
    val requests = obj(artifacts(store)).fields.filter(_._1.endsWith("request.json"))
    store.atomic("prompt-template-hashes.json", Json.canonical(Obj(requests)))
    val provenance = Json.obj("Scala" -> s("3.3.8"), "sbt" -> s("1.10.7"), "JDK" -> s(Runtime.version.toString), "gitCommit" -> s(command("git", "rev-parse", "HEAD")),
      "dirtyTree" -> Bool(command("git", "status", "--porcelain").nonEmpty), "diffHash" -> s(sha(command("git", "diff", "HEAD"))), "sourceHash" -> field(p.config, "sourceHash"),
      "endpoint" -> s("https://api.openai.com/v1/responses"), "requestedModel" -> s(p.model), "frozenAt" -> s(Instant.now.toString))
    store.atomic("provenance.json", Json.canonical(provenance))
    store.atomic("preregistration.md", s"""# Frozen syntax locality experiment
Protocol: `${p.protocolHash}`
Primary: scope_lookup / after_close / named_end minus generic_end; equal weights across lexical regimes and depth × filler cells. Structural families are the independent units. Ten thousand stratified paired cluster bootstrap draws; seed ${num(p.config, "bootstrapSeed")}. Other contrasts are exploratory. Stop at planned count, numerical budget limits, or infrastructure failures; never stop on significance. Pilot results are excluded. Incomplete/refusal are model failures; infrastructure outcomes are missing. Investigate missing rates over 1%. Alias backend cannot be fully fixed; retain returned models/IDs/dates. Prior pilot assessment must be recorded before confirmatory interpretation.
""")
    store.atomic("freeze.json", Json.canonical(Json.obj("protocolHash" -> s(p.protocolHash), "artifacts" -> artifacts(store), "provenance" -> provenance)))
    p.protocolHash
  private def command(args: String*): String =
    val process = new ProcessBuilder(args*).redirectErrorStream(true).start()
    val result = new String(process.getInputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
    process.waitFor()
    result.trim
