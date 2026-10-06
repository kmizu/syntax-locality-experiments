package locality.bench

import java.nio.file.Files
import java.nio.charset.StandardCharsets

object BenchTests:
  def main(args: Array[String]): Unit =
    val selected = if args.isEmpty then "all" else
      require(args.length == 2 && args(0) == "--suite", "usage: --suite NAME")
      args(1)
    val suites = Vector("bootstrap", "oracle", "syntax", "generator", "distribution", "table", "prompt", "tasks", "runner", "safety", "cli", "preflight", "score", "stats", "report", "freeze", "parsing", "full-program-prompt", "full-program")
    require(selected == "all" || suites.contains(selected), s"Unknown suite: $selected")
    var count = 0
    suites.filter(s => selected == "all" || selected == s).foreach { suite =>
      if suite == "bootstrap" then
        assert(Runtime.version().feature() == 21, "JDK 21 is required")
        val path = Files.createTempFile("locality-utf8", ".txt")
        try
          Files.writeString(path, "日本語 🐈\n", StandardCharsets.UTF_8)
          assert(Files.readString(path, StandardCharsets.UTF_8) == "日本語 🐈\n")
        finally Files.delete(path)
      else
        val names = Map("oracle" -> "OracleTests", "syntax" -> "SyntaxTests", "generator" -> "GeneratorTests", "distribution" -> "DistributionTests", "table" -> "TableTests", "prompt" -> "PromptTests", "tasks" -> "TaskTests", "runner" -> "RunnerTests", "safety" -> "RunnerSafetyTests", "cli" -> "CliTests", "preflight" -> "run.PreflightTests", "score" -> "ScoreTests", "stats" -> "StatsTests", "report" -> "ReportTests", "freeze" -> "FreezeTests", "parsing" -> "ParsingTests", "full-program-prompt" -> "FullProgramPromptTests", "full-program" -> "FullProgramPipelineTests")
        val cls = Class.forName(s"locality.bench.${names(suite)}$$")
        val module = cls.getField("MODULE$").get(null)
        cls.getMethod("run").invoke(module)
      count += 1
      println(s"PASS $suite")
    }
    require(count > 0, "Zero tests executed")
    println(s"Passed $count suites")
