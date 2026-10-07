package publication

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardCopyOption}
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

/** Exercises the offline generator with copied saved reports and hand-counted operational fixtures. */
object ExecutionSnapshotTests:
  private def snapshot(dispatched: Long, terminal: Long, infra: Long, statuses: String): String =
    s"""{"capturedAt":"2026-10-07T12:24:25.8694012Z","utcWindow":"2026-10-07","planned":960,"batchLimit":800,"tokenCountAttempts":960,"generationDispatchAttempts":$dispatched,"distinctDispatched":$dispatched,"terminal":$terminal,"infrastructureTerminalCount":$infra,"statuses":$statuses,"actualKnownTokens":202768,"openReservedTokens":119976,"unresolvedIncludesCurrentInFlight":true,"cumulativeCap":19000000,"synthetic_mock":false,"sourceHash":"41ffc2647b8f3c3838a17c2425b0be97007fe611f5d0a68d3dd9d1c79e1b0b96","protocolHash":"2ac04cc60928800d0c1fdd37986546a6fee23bd1955ccc011c76099cbde60471","requestedModel":"gpt-5.6-terra","phase":"p2","runLabel":"full-program-pilot-live-v1","measurementScope":"Execution progress only; not strict grades, accuracy, or inferential statistics."}"""

  def main(args: Array[String]): Unit =
    require(args.length == 1, "Saved report fixture directory required")
    val saved = Path.of(args(0)).toAbsolutePath.normalize
    val scratch = Files.createTempDirectory("syntax-report-snapshot-tests-").toAbsolutePath.normalize
    def generate(name: String, raw: String, recovery: Option[String] = None): Path =
      val input = scratch.resolve(name).resolve("data")
      val output = scratch.resolve(name).resolve("site")
      val paths = Files.walk(saved)
      try paths.iterator.asScala.foreach { path =>
        val target = input.resolve(saved.relativize(path))
        if Files.isDirectory(path) then Files.createDirectories(target)
        else Files.copy(path, target, StandardCopyOption.COPY_ATTRIBUTES)
      }
      finally paths.close()
      val recoveryPath = input.resolve("supporting/live-recovery-20261007.txt")
      Files.deleteIfExists(recoveryPath)
      recovery.foreach(note => Files.writeString(recoveryPath, note, UTF_8))
      Files.writeString(input.resolve("supporting/p2-live-current-status.json"), raw + "\n", UTF_8)
      HtmlReport.main(Array(input.toString, output.toString))
      output
    def accepted(name: String, raw: String, expectedDispatched: String, expectedTerminal: String): Unit =
      val output = generate(name, raw)
      val provenance = Files.readString(output.resolve("build-provenance.json"), UTF_8)
      assert(provenance.contains(s"\"p2LiveExecutionDistinctDispatched\":$expectedDispatched,"), name)
      assert(provenance.contains(s"\"p2LiveExecutionTerminalDispositions\":$expectedTerminal,"), name)
      assert(Files.isRegularFile(output.resolve("index.html")), name)
    def rejected(name: String, raw: String): Unit =
      val error = try { generate(name, raw); None }
        catch { case e: IllegalArgumentException => Some(e) }
      assert(error.exists(_.getMessage.contains("P2 execution")), s"$name must reject an inconsistent snapshot")
      assert(!Files.exists(scratch.resolve(name).resolve("site/index.html")), name)
    val failures = scala.collection.mutable.ArrayBuffer.empty[String]
    def check(name: String)(test: => Unit): Unit =
      try { test; println(s"PASS $name") }
      catch { case NonFatal(e) => failures += s"$name: ${e.getMessage}"; println(s"FAIL $name: ${e.getMessage}") }
    try
      check("expired counts may make terminal exceed dispatch") {
        accepted("expired-counts", snapshot(334, 336, 5,
          """[{"status":"completed","count":329},{"status":"api_rejected","count":5},{"status":"not_dispatched","count":2}]"""), "334", "336")
      }
      check("all planned dispositions retain two unexecuted trials") {
        accepted("all-dispositions", snapshot(958, 960, 5,
          """[{"status":"completed","count":953},{"status":"api_rejected","count":5},{"status":"not_dispatched","count":2}]"""), "958", "960")
      }
      check("count-stage rejection need not be a generation dispatch") {
        accepted("count-rejection", snapshot(334, 337, 6,
          """[{"status":"completed","count":329},{"status":"api_rejected","count":6},{"status":"not_dispatched","count":2}]"""), "334", "337")
      }
      check("completed responses cannot exceed dispatch") {
        rejected("completed-over-dispatch", snapshot(328, 329, 0,
          """[{"status":"completed","count":329}]"""))
      }
      check("unexecuted dispositions cannot overlap the dispatched plan") {
        rejected("overlapping-plan", snapshot(959, 960, 5,
          """[{"status":"completed","count":953},{"status":"api_rejected","count":5},{"status":"not_dispatched","count":2}]"""))
      }
      check("infrastructure count must agree with its status partition") {
        rejected("wrong-infra-count", snapshot(334, 334, 4,
          """[{"status":"completed","count":329},{"status":"api_rejected","count":5}]"""))
      }
      check("unknown terminal status cannot establish progress") {
        rejected("unknown-status", snapshot(334, 334, 5,
          """[{"status":"completed","count":329},{"status":"other_failure","count":5}]"""))
      }
      check("saved recovery note replaces the current interruption notice without changing reports") {
        val note = "TEST-RECOVERY <script> & \"quote\" 'single' — operational fixture only; no measurement claim."
        val output = generate("saved-recovery", snapshot(334, 336, 5,
          """[{"status":"completed","count":329},{"status":"api_rejected","count":5},{"status":"not_dispatched","count":2}]"""), Some(note))
        val html = Files.readString(output.resolve("index.html"), UTF_8)
        assert(html.contains("TEST-RECOVERY &lt;script&gt; &amp; &quot;quote&quot; &#39;single&#39;"),
          "Recovery note must be visible as escaped text")
        assert(!html.contains(note), "Recovery HTML metacharacters must not become markup")
        val current = html.indexOf("実行経過・保存結果")
        val historical = html.indexOf("<details><summary>以前の中断記録（履歴）</summary>")
        assert(current >= 0 && historical > current, "Current recovery note must precede the historical interruption detail")
        val historyEnd = html.indexOf("</details>", historical)
        assert(historyEnd > historical, "Historical interruption must remain a collapsible detail")
        val history = html.substring(historical, historyEnd)
        assert(history.contains("data/supporting/live-interruption.txt"), "Historical note must remain linked in its detail")
        assert(!html.contains("<strong>実行の中断と途中結果。</strong>"), "Historical interruption must not lead as the current blocker")
        assert(java.util.Arrays.equals(Files.readAllBytes(saved.resolve("supporting/live-interruption.txt")),
          Files.readAllBytes(output.resolve("data/supporting/live-interruption.txt"))),
          "Original interruption bytes must be preserved")
        val overall = Files.readAllLines(saved.resolve("p2/summary.csv"), UTF_8).get(1)
          .split(",", -1).toVector.map(_.stripPrefix("\"").stripSuffix("\""))
        assert(overall.take(6).forall(_ == "ALL"), "Saved graded summary must have its overall row first")
        val dispatched = overall(7)
        val terminal = overall(8)
        val provenance = Files.readString(output.resolve("build-provenance.json"), UTF_8)
        assert(provenance.contains("\"p2LiveCountsBasis\":\"graded_report\""), "Recovery note must preserve report-first counts")
        assert(provenance.contains(s"\"p2LiveGenerations\":$dispatched,"), "Saved report dispatch denominator must remain unchanged")
        assert(provenance.contains(s"\"p2LiveTerminalDispositions\":$terminal,"), "Saved report terminal denominator must remain unchanged")
        assert(java.util.Arrays.equals(Files.readAllBytes(saved.resolve("p2/summary.csv")),
          Files.readAllBytes(output.resolve("data/p2/summary.csv"))), "Recovery note must not rewrite saved grades")
        val legacyOutput = scratch.resolve("expired-counts/site/index.html")
        val legacyHtml = Files.readString(legacyOutput, UTF_8)
        assert(legacyHtml.contains("<strong>実行の中断と途中結果。</strong>"), "Absent recovery note must retain the existing interruption behavior")
        Vector("empty-recovery" -> " \n\t", "oversize-recovery" -> ("x" * 4001)).foreach { (name, invalid) =>
          val error = try { generate(name, snapshot(334, 336, 5,
            """[{"status":"completed","count":329},{"status":"api_rejected","count":5},{"status":"not_dispatched","count":2}]"""), Some(invalid)); None }
            catch { case e: IllegalArgumentException => Some(e) }
          assert(error.exists(_.getMessage.contains("saved execution recovery note")), s"$name must reject invalid note text")
          assert(!Files.exists(scratch.resolve(name).resolve("site/index.html")), s"$name must not publish invalid note text")
        }
        println(s"Recovery integration preserves real saved report dispatch=$dispatched / terminal=$terminal; fixture text supplies no grades")
      }
      assert(failures.isEmpty, failures.mkString("\n"))
      println("8 execution snapshot integration checks passed; no model HTTP")
    finally
      val paths = Files.walk(scratch)
      try paths.iterator.asScala.toVector.sortBy(_.getNameCount).reverse.foreach { path =>
        require(path.toAbsolutePath.normalize.startsWith(scratch), "Test cleanup escaped its temporary directory")
        Files.delete(path)
      }
      finally paths.close()
