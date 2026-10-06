package locality.bench

import locality.bench.run.*
import java.nio.file.Files

object FreezeTests:
  def run(): Unit =
    val dir = Files.createTempDirectory("locality-freeze")
    val store = new RunStore(dir)
    try
      val p = Protocol.load("main", "p1")
      store.atomic("protocol.json", locality.llm.json.Json.render(p.json))
      store.atomic("cases.jsonl", "{\"id\":\"one\"}\n")
      store.atomic("gold.jsonl", "{\"id\":\"one\"}\n")
      store.atomic("families.jsonl", "{\"id\":\"one\"}\n")
      store.atomic("schedule.jsonl", "{\"id\":\"one\"}\n")
      store.atomic("manifest.json", "{}")
      store.atomic("trials/one/request.json", "{}")
      assert(!Freeze.verify(store, p.protocolHash), "unfrozen main must be rejected")
      val capabilities = dir.resolve("probe.json")
      Files.writeString(capabilities, s"""{"model":"${p.model}","reasoningEffort":"low","tokenCountSupported":true,"generationSupported":true}""")
      val rejected = try
        Freeze.create(store, capabilities)
        false
      catch case _: IllegalArgumentException => true
      assert(rejected, "Freeze must reject an invalid unaudited dataset")
    finally store.close()
    val validDir = Files.createTempDirectory("locality-freeze-valid")
    import locality.llm.json.*
    import locality.bench.run.JsonSupport.*
    val raw = obj(parse(Files.readString(java.nio.file.Path.of("configs/main.json"))))
    val overrides = Vector("depths" -> Arr(Vector(n(2))), "fillersReading" -> Arr(Vector(n(0))), "seedsPerCell" -> n(1))
    val configFile = Files.createTempFile("locality-freeze-config", ".json")
    Files.writeString(configFile, Json.canonical(Obj(raw.fields.filterNot(v => overrides.exists(_._1 == v._1)) ++ overrides)))
    val p = Protocol.load("main", "p1", Some(configFile))
    Planner.save(p, validDir)
    val valid = new RunStore(validDir)
    try
      assert(!Freeze.verify(valid, p.protocolHash))
      val neverCalled = new locality.llm.LlmClient:
        def countInputTokens(request: locality.llm.LlmRequest) = throw new AssertionError("Unfrozen main must not count")
        def generate(request: locality.llm.LlmRequest) = throw new AssertionError("Unfrozen main must not generate")
      val unfrozenRunnerRejected = try
        Runner.execute(valid, neverCalled, Limits(1, 2, 100000), execute = true)
        false
      catch case _: IllegalArgumentException => true
      assert(unfrozenRunnerRejected, "Public Runner boundary must reject unfrozen main")
      val capabilities = validDir.resolve("fixture-capabilities.json")
      val validProbe = s"""{"model":"${p.model}","reasoningEffort":"low","maxOutputTokensReading":8192,"tokenCountSupported":true,"generationSupported":true,"verified":true,"synthetic_mock":false,"generationResponseId":"fixture-response","endpoint":"https://api.openai.com/v1"}"""
      Files.writeString(capabilities, validProbe.replace("8192", "4096"))
      val incompatibleCapRejected = try
        Freeze.create(valid, capabilities)
        false
      catch case _: IllegalArgumentException => true
      assert(incompatibleCapRejected, "Freeze must reject settings that were not capability-probed")
      Files.writeString(capabilities, validProbe)
      assert(Freeze.create(valid, capabilities) == p.protocolHash)
      assert(Freeze.verify(valid, p.protocolHash))
      assert(!Freeze.verify(valid, "wrong"))
      Vector("capabilities.json", "preregistration.md", "dataset-manifest.json", "prompt-template-hashes.json", "provenance.json").foreach { name =>
        val original = valid.read(name)
        valid.atomic(name, original + "\n")
        assert(!Freeze.verify(valid, p.protocolHash), s"Mutation of frozen $name must be rejected")
        valid.atomic(name, original)
      }
      val trial = str(valid.jsonLines("cases.jsonl").head, "id")
      valid.atomic(s"trials/$trial/request.json", "{}")
      assert(!Freeze.verify(valid, p.protocolHash), "Modified request must invalidate freeze")
    finally valid.close()
