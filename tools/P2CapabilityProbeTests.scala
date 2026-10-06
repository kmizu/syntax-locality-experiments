package locality.bench.verification

import locality.bench.run.*
import locality.bench.run.JsonSupport.*
import locality.llm.*
import locality.llm.json.*
import java.nio.file.{Files, Path}

object P2CapabilityProbeTests:
  private val protocol = Protocol("pilot", "p2", Json.obj("model" -> s("gpt-5.6-terra"),
    "reasoningEffort" -> s("low"), "maxOutputTokensReading" -> n(8192),
    "maxOutputTokensGeneration" -> n(16384), "sourceHash" -> s("41ffc2647b8f3c3838a17c2425b0be97007fe611f5d0a68d3dd9d1c79e1b0b96")), "test-protocol")
  private def read(dir: Path, name: String): JsonValue = parse(Files.readString(dir.resolve(name)))
  private def rejects(body: => Unit): Unit =
    val rejected = try { body; false } catch case _: Exception => true
    assert(rejected, "Unsafe or failed probe must reject")
  private final class Fixture(count: Either[LlmError, Long] = Right(20),
    generation: Either[LlmError, LlmResponse] = Right(LlmResponse("fixture-response", "fixture-model", "completed", "5916", Vector.empty,
      None, Some(TokenUsage(20, 5, 25, Some(0), None, Some(0))), Some("fixture-request"), "{\"id\":\"fixture-response\"}"))) extends LlmClient:
    var counts = 0
    var generations = 0
    def countInputTokens(request: LlmRequest): Either[LlmError, Long] =
      counts += 1
      assert(request.model == "gpt-5.6-terra" && request.reasoningEffort.contains("low") && request.maxOutputTokens == 16384)
      count
    def generate(request: LlmRequest): Either[LlmError, LlmResponse] =
      generations += 1
      assert(request.input == Vector(TextMessage("user", "Return only 5916.")))
      generation
  def main(args: Array[String]): Unit =
    val success = Files.createTempDirectory("p2-probe-success")
    P2CapabilityProbe.probe(success, protocol, new Fixture(), Limits(1, 2, 20000), synthetic = true)
    val capabilities = read(success, "capabilities.json")
    assert(bool(field(capabilities, "verified")) && bool(field(capabilities, "synthetic_mock")))
    assert(num(capabilities, "maxOutputTokensFullProgram") == 16384)
    assert(str(capabilities, "protocolHash") == "test-protocol")
    assert(num(capabilities, "knownTokens") == 25 && num(capabilities, "unresolvedTokens") == 0)
    assert(num(read(success, "request.json"), "max_output_tokens") == 16384)
    assert(read(success, "count-attempt-001-request.json").asInstanceOf[Obj].get("max_output_tokens").isEmpty)
    assert(Files.readString(success.resolve("usage-ledger.jsonl")).linesIterator.size == 2)
    println("PASS P2 capability probe: matching cap, source/protocol identity, exact saved requests and settled usage")
    val before = Files.readString(success.resolve("capabilities.json"))
    val duplicate = new Fixture()
    rejects { P2CapabilityProbe.probe(success, protocol, duplicate, Limits(1, 2, 20000), synthetic = true) }
    assert(duplicate.counts == 0 && duplicate.generations == 0 && Files.readString(success.resolve("capabilities.json")) == before)
    println("PASS P2 capability probe: initialized output refuses without resending or overwriting")
    val failedCount = Files.createTempDirectory("p2-probe-count-failure")
    val countClient = new Fixture(count = Left(TransportError("fixture count failed", false)))
    rejects { P2CapabilityProbe.probe(failedCount, protocol, countClient, Limits(1, 2, 20000), synthetic = true) }
    assert(countClient.generations == 0 && !Files.exists(failedCount.resolve("usage-ledger.jsonl")))
    assert(!bool(field(read(failedCount, "capabilities.json"), "verified")))
    println("PASS P2 capability probe: failed count stops before generation or reservation")
    val failedGeneration = Files.createTempDirectory("p2-probe-generation-failure")
    rejects { P2CapabilityProbe.probe(failedGeneration, protocol, new Fixture(generation = Left(TransportError("fixture uncertain", true))), Limits(1, 2, 20000), synthetic = true) }
    assert(num(read(failedGeneration, "capabilities.json"), "unresolvedTokens") == 16660)
    assert(Files.readString(failedGeneration.resolve("usage-ledger.jsonl")).linesIterator.size == 1)
    println("PASS P2 capability probe: ambiguous generation preserves the full reservation")
    val unknownUsage = Files.createTempDirectory("p2-probe-unknown-usage")
    val responseWithoutUsage = LlmResponse("fixture-response", "fixture-model", "completed", "5916", Vector.empty, None, None, None, "{\"id\":\"fixture-response\"}")
    rejects { P2CapabilityProbe.probe(unknownUsage, protocol, new Fixture(generation = Right(responseWithoutUsage)), Limits(1, 2, 20000), synthetic = true) }
    assert(num(read(unknownUsage, "capabilities.json"), "unresolvedTokens") == 16660)
    assert(num(read(unknownUsage, "capabilities.json"), "knownTokens") == 0)
    println("PASS P2 capability probe: unknown usage never settles as zero")
    Vector(Limits(1, 1, 20000), Limits(1, 2, 16000)).foreach { limit =>
      val dir = Files.createTempDirectory("p2-probe-impossible-limit")
      val client = new Fixture()
      rejects { P2CapabilityProbe.probe(dir, protocol, client, limit, synthetic = true) }
      assert(client.counts == 0 && client.generations == 0 && !Files.exists(dir.resolve("request.json")))
    }
    println("PASS P2 capability probe: impossible token or HTTP limits refuse before network")
    println("Passed 6 P2 capability probe cases; synthetic fixtures only, zero live HTTP")
