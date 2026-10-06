package locality.bench

import locality.bench.report.*
import locality.bench.score.*
import locality.bench.stats.*
import java.nio.file.Files

object ReportTests:
  def run(): Unit =
    val rows = StatsTests.fixture()
    val mixed = rows.take(6).zipWithIndex.map { (r, i) =>
      i match
        case 0 => r.copy(score = Score(Outcome.Correct, true))
        case 1 => r.copy(score = Score(Outcome.Refusal, false))
        case 2 => r.copy(score = Score(Outcome.IncompleteOutput, false))
        case 3 => r.copy(score = Score(Outcome.TransportFailure, false))
        case 4 => r.copy(score = Score(Outcome.AmbiguousOutcome, false), terminal = false)
        case _ => r.copy(score = Score(Outcome.NotDispatched, false), dispatched = false, terminal = false)
    }
    val d = Report.denominators(mixed)
    assert(d.planned == 6 && d.dispatched == 5 && d.terminal == 4)
    assert(d.evaluable == 3 && d.correct == 1 && d.infrastructureMissing == 2 && d.notDispatched == 1)
    assert(d.incomplete == 1 && d.refusal == 1)
    assert(d.operationalLower.contains(0.2) && d.operationalUpper.contains(0.4))
    val directory = Files.createTempDirectory("locality-report-test")
    val metadata = ReportMetadata("smoke", "p0", "protocol-test", "mock", "2026-10-05T00:00:00Z", true)
    Report.write(directory, metadata, rows)
    val report = Files.readString(directory.resolve("report.md"))
    assert(report.contains("synthetic_mock=true"))
    assert(report.contains("not_computed"), "smoke is wiring verification, with no confidence interval")
    assert(report.contains("25.000"), "report must contain +25 percentage points")
    assert(Files.exists(directory.resolve("summary.csv")))
    assert(Files.exists(directory.resolve("paired-comparisons.csv")))
    assert(Files.exists(directory.resolve("errors.csv")))
    val before = Files.readString(directory.resolve("scores.jsonl"))
    Report.write(directory, metadata, rows)
    assert(before == Files.readString(directory.resolve("scores.jsonl")), "saved results produce deterministic scoring output")
    val usage = TokenUsage(Some(100), Some(20), Some(10), Some(50))
    val withUsage = rows.map(r => r.copy(usage = Some(usage), generationAttempts = 1, attemptUsage = Vector(usage)))
    Report.write(directory, metadata, withUsage)
    val csv = Files.readString(directory.resolve("summary.csv")).linesIterator.toVector.map(_.split(",").toVector.map(_.stripPrefix("\"").stripSuffix("\"")))
    val tokensColumn = csv.head.indexOf("tokens_per_correct")
    assert(csv.find(r => r.head == "scope_lookup" && r(2) == "generic_end" && r(3) == "ALL").get(tokensColumn) == "240.000")
    Report.write(directory, metadata, withUsage.map(r => r.copy(score = Score(Outcome.WrongAnswer, false))))
    val zeroCsv = Files.readString(directory.resolve("summary.csv")).linesIterator.toVector.map(_.split(",").toVector.map(_.stripPrefix("\"").stripSuffix("\"")))
    assert(zeroCsv.tail.forall(_(tokensColumn) == "NA"), "zero correct answers cannot yield zero tokens/correct")
    Report.write(directory, metadata, withUsage.map(r => r.copy(generationAttempts = 2, attemptUsage = Vector(usage, usage))))
    val retryCsv = Files.readString(directory.resolve("summary.csv")).linesIterator.toVector.map(_.split(",").toVector.map(_.stripPrefix("\"").stripSuffix("\"")))
    val attemptColumn = retryCsv.head.indexOf("attempt_tokens_per_correct")
    assert(attemptColumn >= 0, "actual retry cost must have a separate tokens/correct column")
    assert(retryCsv.find(r => r.head == "scope_lookup" && r(2) == "generic_end" && r(3) == "ALL").get(attemptColumn) == "480.000")
    val totalRow = retryCsv.find(r => r.take(6) == Vector.fill(6)("ALL"))
    assert(totalRow.isDefined && totalRow.get(retryCsv.head.indexOf("planned")) == "16",
      "the report's overall denominator table must also have an exact CSV row")
