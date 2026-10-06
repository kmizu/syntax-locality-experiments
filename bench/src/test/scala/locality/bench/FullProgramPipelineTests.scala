package locality.bench

import locality.bench.data.*
import locality.bench.lang.*
import locality.bench.run.*
import locality.bench.run.JsonSupport.*
import locality.llm.json.*
import java.nio.file.{Files, Path}

object FullProgramPipelineTests:
  def main(args: Array[String]): Unit = run()

  private def rejected(body: => Unit): Unit =
    val failed = try
      body
      false
    catch case _: IllegalArgumentException => true
    assert(failed, "Invalid complete-program plan must fail before dispatch")

  private def config(updates: (String, JsonValue)*): Path =
    val base = obj(parse(Files.readString(Path.of("configs/pilot.json"))))
    val file = Files.createTempFile("locality-full-program-config", ".json")
    val fields = base.fields.filterNot(f => updates.exists(_._1 == f._1)) ++ updates
    Files.writeString(file, Json.canonical(Obj(fields)))
    file

  private val completeTasks = Arr(Vector(s("ast_to_source"), s("source_to_ast")))

  def run(): Unit =
    val fullConfig = config("tasks" -> completeTasks, "depths" -> Arr(Vector(n(2), n(4), n(8), n(16))),
      "fillersGeneration" -> Arr(Vector(n(0), n(8), n(32))), "seedsPerCell" -> n(4), "masterSeed" -> n(2026100602L))
    try
      val p = Protocol.load("pilot", "p2", Some(fullConfig), Some("test-mock"))
      val planned = Planner.build(p)
      assert(planned.size == 960, "P2 must plan both complete directions, not legacy prefix tasks")
      assert(planned.map(_.expanded.family.id).distinct.size == 48, "Directions must share 48 structural families")
      assert(planned.map(_.expanded.task.id).toSet == Set("ast_to_source", "source_to_ast"))
      assert(planned.forall(t => t.expanded.view.isEmpty && t.request.maxOutputTokens == 16384))
      planned.groupBy(_.expanded.family.id).foreach { (_, familyTrials) =>
        assert(familyTrials.size == 20, "One family has two directions × two lexical regimes × five styles")
        assert(familyTrials.map(_.expanded.family.program).distinct.size == 1)
        assert(familyTrials.filter(_.expanded.spec.lexicon.regime == "nonce").map(_.expanded.spec.lexicon.setId).distinct.size == 1)
        assert(familyTrials.map(t => (t.expanded.task.id, t.expanded.spec.lexicon.regime, t.expanded.spec.style, t.replicate)).distinct.size == 20)
      }
      planned.groupBy(t => (t.expanded.family.peakDepth, t.expanded.family.fillerStatements)).foreach { (_, cell) =>
        assert(cell.map(_.expanded.family.id).distinct.size == 4)
      }
      planned.filter(_.expanded.task.id == "source_to_ast").foreach { trial =>
        val c = trial.expanded
        assert(Parser.parseProgram(c.source, c.spec) == Right(c.family.program), "Parsing must receive the complete source")
        assert(trial.request.input.exists(_.content.contains(c.source)), "Manual parsing input must include full source")
        assert(trial.gold.get("ast").nonEmpty, "Manual parsing requires separate saved AST gold")
      }
      planned.filter(_.expanded.task.id == "ast_to_source").foreach { trial =>
        assert(!trial.request.input.exists(_.content.contains(trial.expanded.goldSource)), "Generation must not receive its complete source answer")
      }
      rejected { Protocol.load("main", "p2", Some(fullConfig), Some("test-mock")); () }
    finally Files.delete(fullConfig)

    Vector(
      Arr(Vector(s("source_to_ast"), s("source_to_ast"))),
      Arr(Vector(s("unknown_task"))), Arr(Vector(s("scope_lookup"))),
      Arr(Vector.empty), Str("source_to_ast")
    ).foreach { tasks =>
      val path = config("tasks" -> tasks)
      try rejected { Protocol.load("pilot", "p2", Some(path), Some("test-mock")); () }
      finally Files.delete(path)
    }
    val excessiveDepth = config("tasks" -> completeTasks, "depths" -> Arr(Vector(n(33))))
    try rejected { Planner.build(Protocol.load("pilot", "p2", Some(excessiveDepth), Some("test-mock"))); () }
    finally Files.delete(excessiveDepth)

    val smallConfig = config("depths" -> Arr(Vector(n(2))), "fillersReading" -> Arr(Vector(n(0))),
      "fillersGeneration" -> Arr(Vector(n(0))), "seedsPerCell" -> n(1), "fewShotCount" -> n(2),
      "masterSeed" -> n(2026100602L), "maxRpm" -> n(10000), "maxTpm" -> n(10000000))
    try
      val legacy = Planner.build(Protocol.load("pilot", "p1", Some(smallConfig), Some("test-mock")))
      assert(legacy.size == 50 && legacy.map(_.expanded.task.id).toSet == Set("scope_lookup", "active_stack", "ast_to_source"),
        "Adding Task.SourceToAst must not alter default P1 tasks/counts")
      assert(legacy.forall(_.gold.get("ast").isEmpty), "Legacy gold schema must remain unchanged")
      val dir = Files.createTempDirectory("locality-full-program-mock")
      Planner.save(Protocol.load("pilot", "p2", Some(smallConfig), Some("test-mock")), dir)
      val store = new RunStore(dir)
      try
        val result = Runner.execute(store, new MockClient(store), Limits(20, 40, 1000000), mock = true)
        assert(result.logicalCalls == 20 && result.httpAttempts == 40)
        val scores = Results.observations(store)
        assert(scores.size == 20 && scores.forall(_.score.strictCorrect), "Saved manual-AST mock output must grade exactly")
        assert(scores.map(_.task).toSet == Set("ast_to_source", "source_to_ast"))
        Results.write(store)
        val report = store.read("report.md")
        assert(report.contains("synthetic_mock=true") && report.contains("source_to_ast") && report.contains("ast_to_source"))
        assert(report.contains("exploratory") && report.contains("depth") && report.contains("filler"))
        assert(!report.contains("## 4. Primary paired D−B comparison"), "P2 has no legacy reading-primary estimand")
        assert(!report.contains("shorter capped AST bodies"), "P2 depth16/full-program plans must not inherit P1 body-size claim")
        assert(Runner.execute(store, new MockClient(store), Limits(20, 40, 1000000), mock = true).logicalCalls == 0,
          "Terminal parsing/generation trials must resume without resend")
      finally store.close()
    finally Files.delete(smallConfig)
