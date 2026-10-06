package locality.bench

import locality.bench.run.*
import java.nio.file.Files
import java.util.concurrent.Executors

object RunnerTests:
  def run(): Unit =
    val budget = new Budget(Limits(20, 40, 10000), 0, Vector.empty)
    val pool = Executors.newFixedThreadPool(4)
    try
      val futures = (0 until 40).map(i => pool.submit(new java.util.concurrent.Callable[Boolean]:
        def call(): Boolean = budget.start(s"trial-$i", 1000)))
      assert(futures.count(_.get()) == 10, "concurrent reservations must obey token cap")
      assert(budget.reservedTokens == 10000)
    finally pool.shutdown()
    budget.settle("trial-0", Some(100))
    assert(budget.knownTokens == 100)
    assert(budget.reservedTokens == 9000)
    assert(!budget.start("extra", 1000))
    assert(budget.start("small", 900))
    budget.settle("small", None)
    assert(budget.reservedTokens == 9900, "unknown usage must retain reservation")
    val restored = new Budget(Limits(2, 3, 10000), 100, Vector("unresolved" -> 9900L))
    assert(!restored.start("new", 1))
    val calls = new Budget(Limits(1, 2, 20000), 0, Vector.empty)
    assert(calls.start("one", 100)); assert(!calls.start("two", 100))
    assert(calls.http()); assert(calls.http()); assert(!calls.http())
    val retryBudget = new Budget(Limits(2, 8, 10000), 0, Vector.empty)
    assert(retryBudget.start("trial/1", 400))
    retryBudget.settle("trial/1", None)
    assert(!retryBudget.reserveRetry("trial/1", 400), "Each physical attempt needs a distinct reservation ID")
    assert(retryBudget.reserveRetry("trial/2", 400))
    retryBudget.settle("trial/2", Some(100))
    assert(retryBudget.knownTokens == 100 && retryBudget.reservedTokens == 400)
    val dir = Files.createTempDirectory("locality-store")
    val store = new RunStore(dir)
    try
      store.atomic("terminal.json", "{\"ok\":true}")
      assert(store.read("terminal.json") == "{\"ok\":true}")
      val locked = try
        val other = new RunStore(dir); other.close(); false
      catch case _: IllegalStateException => true
      assert(locked, "run directory must have a single writer")
      store.append("events.jsonl", "{\"a\":1}")
      Files.writeString(dir.resolve("events.jsonl"), "{\"unfinished\":", java.nio.file.StandardOpenOption.APPEND)
      assert(store.jsonLines("events.jsonl").size == 1)
      assert(Files.exists(dir.resolve("events.jsonl.quarantine")))
      Files.writeString(dir.resolve("complete-last.jsonl"), "{\"a\":1}\n{\"b\":2}")
      assert(store.jsonLines("complete-last.jsonl").size == 2, "A complete final JSON record must survive missing LF")
      Files.writeString(dir.resolve("bad.jsonl"), "bad\n{\"a\":1}\n")
      val rejected = try
        store.jsonLines("bad.jsonl")
        false
      catch case _: IllegalArgumentException => true
      assert(rejected, "middle corruption must stop resume")
    finally store.close()
    assert(Planner.build(Protocol.load("smoke", "p0")).size == 20)
    assert(Planner.build(Protocol.load("smoke", "p1")).size == 60)
    assert(Planner.build(Protocol.load("pilot", "p0")).size == 720)
    assert(Planner.build(Protocol.load("pilot", "p1")).size == 1800)
    assert(Planner.build(Protocol.load("main", "p1")).size == 5120)
    val run = Files.createTempDirectory("locality-runner")
    Planner.save(Protocol.load("smoke", "p0"), run)
    val saved = new RunStore(run)
    class FixtureClient extends locality.llm.LlmClient:
      var generations = 0
      var counts = 0
      def countInputTokens(r: locality.llm.LlmRequest) = synchronized {
        counts += 1
        Right(100L)
      }
      def generate(r: locality.llm.LlmRequest) = synchronized {
        generations += 1
        Right(locality.llm.LlmResponse(s"mock-$generations", r.model, "completed", "5916", Vector.empty, None,
          Some(locality.llm.TokenUsage(100, 1, 101, None, None, None)), None,
          s"""{"id":"mock-$generations","model":"${r.model}","status":"completed","output":[{"type":"message","role":"assistant","content":[{"type":"output_text","text":"5916"}]}],"usage":{"input_tokens":100,"output_tokens":1,"total_tokens":101}}"""))
      }
    val client = new FixtureClient
    try
      assert(Runner.execute(saved, client, Limits(10, 25, 250000)).httpAttempts == 0)
      assert(client.generations == 0 && client.counts == 0, "No execute means no network")
      assert(Runner.execute(saved, client, Limits(10, 25, 250000), execute = true, mock = true).logicalCalls == 10)
      assert(client.generations == 10)
      assert(Runner.execute(saved, client, Limits(10, 25, 250000), execute = true, mock = true).logicalCalls == 10)
      assert(client.generations == 20, "terminal errors/wrong answers must not be resent")
      assert(Runner.execute(saved, client, Limits(10, 25, 250000), execute = true, mock = true).logicalCalls == 0)
      assert(client.generations == 20)
      val originalGold = saved.read("gold.jsonl")
      saved.atomic("gold.jsonl", originalGold.replace("5916", "9999") + "\n")
      val alteredGoldRejected = try
        Results.write(saved)
        false
      catch case _: IllegalArgumentException => true
      assert(alteredGoldRejected, "Report must reject edited gold rather than publish new scores")
      saved.atomic("gold.jsonl", originalGold)
      val firstTrial = JsonSupport.str(saved.jsonLines("schedule.jsonl").head, "id")
      val original = saved.read(s"trials/$firstTrial/attempt-001-body.txt")
      saved.atomic(s"trials/$firstTrial/attempt-001-body.txt", original + "changed")
      val backingRejected = try
        Runner.execute(saved, client, Limits(10, 25, 250000), execute = true, mock = true)
        false
      catch case _: IllegalArgumentException => true
      assert(backingRejected, "Resume must reject missing or corrupted attempt backing files")
    finally saved.close()
