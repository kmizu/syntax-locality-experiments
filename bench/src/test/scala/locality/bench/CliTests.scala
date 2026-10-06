package locality.bench

import locality.bench.cli.Main
import locality.bench.run.*
import locality.bench.run.JsonSupport.*
import locality.llm.json.*
import java.nio.file.{Files, Path}

object CliTests:
  private def rejected(body: => Unit): Unit =
    val failed = try
      body
      false
    catch case _: IllegalArgumentException => true
    assert(failed, "Invalid input must fail before any live request")

  def run(): Unit =
    Vector(
      Vector("unknown"), Vector("plan", "--unknown"),
      Vector("plan", "--out", "a", "--out", "b"),
      Vector("plan", "--preset", "invalid", "--out", "unused"),
      Vector("plan", "--phase", "invalid", "--out", "unused"),
      Vector("preflight", "--max-http-attempts", "0"),
      Vector("run", "--local-token-cap", "-1"),
      Vector("plan", "--count-tokens", "--out", "unused"),
      Vector("plan", "--execute", "--out", "unused"),
      Vector("score", "--execute")
    ).foreach(args => rejected(Main.dispatch(args)))
    Main.dispatch(Vector("preflight")) // plan-only: no key, HTTP client, or output directory needed
    val raw = obj(parse(Files.readString(Path.of("configs/smoke.json"))))
    def changed(key: String, value: JsonValue): Path =
      val file = Files.createTempFile("locality-invalid-config", ".json")
      Files.writeString(file, Json.canonical(Obj(raw.fields.filterNot(_._1 == key) :+ (key -> value))))
      file
    Vector("unknown" -> n(1), "reasoningEffort" -> s("minimal"), "replicates" -> n(0),
      "depths" -> Arr(Vector(n(1))), "fillersReading" -> Arr(Vector(n(-1))),
      "sourceHash" -> s("injected"), "generatorVersion" -> s("injected"),
      "depths" -> Arr(Vector(n(2), n(2))), "fillersReading" -> Arr(Vector(n(0), n(0)))
    ).foreach { (key, value) =>
      val file = changed(key, value)
      try rejected { Protocol.load("smoke", "p0", Some(file)); () }
      finally Files.delete(file)
    }
    val out = Files.createTempDirectory("locality-cli-dry")
    Planner.save(Protocol.load("smoke", "p0"), out)
    Main.dispatch(Vector("run", "--run", out.toString))
    assert(!Files.exists(out.resolve("events.jsonl")) && !Files.exists(out.resolve("execution-mode.json")))
    rejected(Main.dispatch(Vector("run", "--run", out.toString, "--execute", "--mock")))
    rejected(Main.dispatch(Vector("resume", "--run", out.toString, "--model", "different-model")))
