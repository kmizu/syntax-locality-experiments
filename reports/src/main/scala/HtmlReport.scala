package publication

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, LinkOption, Path, StandardCopyOption}
import java.security.MessageDigest
import scala.jdk.CollectionConverters.*

/** Offline presentation of persisted grading/report artifacts. No model calls or resampling. */
object HtmlReport:
  private val styles = Vector("braces", "generic_end", "typed_end", "named_end", "padded_end")
  private val labels = Map("braces" -> "A · braces", "generic_end" -> "B · generic end",
    "typed_end" -> "C · typed end", "named_end" -> "D · named end", "padded_end" -> "E · padded end")
  private val requiredFiles = Vector("summary.csv", "paired-comparisons.csv", "errors.csv", "report.md", "protocol.json")
  private val dimensions = Vector("task", "view", "style", "lexical_regime", "depth", "filler")
  private val additive = Vector("planned", "dispatched", "terminal", "model_evaluable", "correct",
    "infrastructure_missing", "not_dispatched", "incomplete", "refusal")
  private val p2MockWitnessPath = "supporting/p2-mock-verification.json"
  private val p2MockWitnessSha256 = "224e9091619a7ce85cce16c663fff2238b3694161fc4f5540734bfd383b5c4dc"
  private val p2MockSourceHash = "41ffc2647b8f3c3838a17c2425b0be97007fe611f5d0a68d3dd9d1c79e1b0b96"
  private val p2MockWorkflowRunUrl = "https://github.com/kmizu/syntax-locality-experiments/actions/runs/37371856651"
  private val p2LiveStatusPath = "supporting/p2-live-current-status.json"
  private val p2LiveProtocolHash = "2ac04cc60928800d0c1fdd37986546a6fee23bd1955ccc011c76099cbde60471"

  final case class ExecutionSnapshot(capturedAt: String, utcWindow: String, planned: Long,
    distinctDispatched: Long, terminal: Long, actualKnownTokens: Long, openReservedTokens: Long,
    unresolvedIncludesCurrentInFlight: Boolean, sha256: String)

  /** The saved heartbeat has a narrow JSON schema, not arbitrary application JSON. */
  private enum ExecutionJson:
    case Text(value: String)
    case Number(value: Long)
    case Bool(value: Boolean)
    case Object(fields: Map[String, ExecutionJson])
    case Array(values: Vector[ExecutionJson])

  private final class ExecutionJsonReader(raw: String):
    private var offset = 0
    private def fail(reason: String): Nothing =
      throw IllegalArgumentException(s"Malformed P2 execution snapshot at character $offset: $reason")
    private def whitespace(): Unit =
      while offset < raw.length && " \t\r\n".contains(raw.charAt(offset)) do offset += 1
    private def accept(c: Char): Boolean =
      if offset < raw.length && raw.charAt(offset) == c then
        offset += 1
        true
      else false
    private def expect(c: Char): Unit = if !accept(c) then fail(s"Expected '$c'")
    private def string(): String =
      expect('"')
      val start = offset
      while offset < raw.length && raw.charAt(offset) != '"' do
        val c = raw.charAt(offset)
        if c < ' ' || c == '\\' || Character.isSurrogate(c) then
          fail("Control characters, escapes and surrogates are unsupported in this snapshot schema")
        offset += 1
      val value = raw.substring(start, offset)
      expect('"')
      value
    private def value(depth: Int): ExecutionJson =
      import ExecutionJson.*
      whitespace()
      if depth > 8 || offset >= raw.length then fail("Unexpected end or excessive depth")
      raw.charAt(offset) match
        case '"' => Text(string())
        case '{' =>
          offset += 1
          whitespace()
          val fields = scala.collection.mutable.Map.empty[String, ExecutionJson]
          def field(): Unit =
            whitespace()
            val key = string()
            if fields.contains(key) then fail("Duplicate object key")
            whitespace()
            expect(':')
            fields(key) = value(depth + 1)
            whitespace()
          if !accept('}') then
            field()
            while accept(',') do field()
            expect('}')
          Object(fields.toMap)
        case '[' =>
          offset += 1
          whitespace()
          val values = Vector.newBuilder[ExecutionJson]
          if !accept(']') then
            values += value(depth + 1)
            whitespace()
            while accept(',') do
              values += value(depth + 1)
              whitespace()
            expect(']')
          Array(values.result())
        case 't' if raw.startsWith("true", offset) => offset += 4; Bool(true)
        case 'f' if raw.startsWith("false", offset) => offset += 5; Bool(false)
        case c if c >= '0' && c <= '9' =>
          val start = offset
          while offset < raw.length && raw.charAt(offset) >= '0' && raw.charAt(offset) <= '9' do offset += 1
          val number = raw.substring(start, offset)
          if !number.matches("0|[1-9][0-9]*") then fail("Expected a canonical non-negative integer")
          try Number(number.toLong)
          catch case _: NumberFormatException => fail("Integer exceeds Long range")
        case _ => fail("Unsupported JSON value in execution snapshot")
    def read(): ExecutionJson =
      require(raw.length <= 16384, "P2 execution snapshot exceeds its schema size limit")
      val result = value(0)
      whitespace()
      if offset != raw.length then fail("Trailing input")
      result

  final case class Row(values: Map[String, String]):
    def apply(key: String): String = values.getOrElse(key, throw IllegalArgumentException(s"Missing CSV column: $key"))
    def number(key: String): Long =
      val value = apply(key)
      require(value.matches("[0-9]+"), s"Expected a non-negative integer in $key, received $value")
      value.toLong

  final case class Run(name: String, summary: Vector[Row], comparisons: Vector[Row], errors: Vector[Row], protocol: String):
    val total: Row =
      val totals = summary.filter(r => dimensions.forall(k => r(k) == "ALL"))
      require(totals.size == 1, s"$name must have exactly one overall summary")
      totals.head
    val leaves: Vector[Row] = summary.filter(r => dimensions.forall(k => r(k) != "ALL"))
    require(leaves.nonEmpty, s"$name has no disjoint summary cells")
    require(leaves.map(r => dimensions.map(r(_))).distinct.size == leaves.size, s"$name has duplicate summary cells")
    require(leaves.forall(r => styles.contains(r("style"))), s"$name contains an unsupported style")
    additive.foreach { key =>
      require(leaves.map(_.number(key)).sum == total.number(key), s"$name leaf/aggregate $key mismatch")
    }
    summary.foreach { row =>
      require(row.number("correct") <= row.number("model_evaluable"), s"$name correct exceeds evaluable")
      require(row.number("model_evaluable") <= row.number("dispatched"), s"$name evaluable exceeds dispatched")
      require(row.number("terminal") <= row.number("planned"), s"$name terminal exceeds planned")
      require(row.number("dispatched") <= row.number("planned"), s"$name dispatched exceeds planned")
    }
    require(errors.size.toLong == total.number("planned") - total.number("correct"),
      s"$name error rows must preserve every non-correct planned trial, including missing/unexecuted trials")
    require(errors.map(_("trial_id")).distinct.size == errors.size, s"$name has duplicate error trial IDs")
    def wholeStyle(task: String, view: String, style: String): Row =
      val found = summary.filter(r => r("task") == task && r("view") == view && r("style") == style &&
        Vector("lexical_regime", "depth", "filler").forall(k => r(k) == "ALL"))
      require(found.size == 1, s"Missing or duplicate persisted $name/$task/$view/$style summary")
      found.head
    def sum(task: String, view: String, style: String, depth: String, field: String): Long =
      leaves.filter(r => r("task") == task && r("view") == view && r("style") == style && r("depth") == depth)
        .map(_.number(field)).sum

  def main(args: Array[String]): Unit =
    require(Runtime.version().feature() == 21, "JDK 21 is required")
    require(args.length <= 2, "Usage: HtmlReport [data-directory] [site-directory]")
    val input = Path.of(args.headOption.getOrElse("data")).toAbsolutePath.normalize()
    val output = Path.of(args.lift(1).getOrElse("site")).toAbsolutePath.normalize()
    require(Files.isDirectory(input), s"Missing publication data directory: $input")
    require(!input.startsWith(output) && !output.startsWith(input), "Input and output directories must not overlap")
    val pilot = readRun(input, "pilot")
    val main = readRun(input, "main")
    val p2 = if Files.isDirectory(input.resolve("p2")) then Some(readRun(input, "p2")) else None
    val p2Execution = readP2ExecutionSnapshot(input)
    require(pilot.total.number("planned") == 1800 && pilot.total.number("terminal") == 1800 &&
      pilot.total.number("model_evaluable") == 1800 && pilot.total.number("correct") == 1622,
      "This report requires the complete original P1 pilot; do not pool changed protocols")
    require(main.total.number("planned") == 5120, "Expected the original frozen 5,120-trial main plan")
    require(protocolScalar(pilot.protocol, "protocolHash") ==
      "f7dd15dd3c44b507506e123517157926a35a53b52c1d4aab278664f512a1e182", "Unexpected original P1 protocol")
    require(protocolScalar(main.protocol, "protocolHash") ==
      "f57aadd6b497ff6d05715753dd59802be050dce8ec5706ff41216721515e6f4a", "Unexpected frozen main protocol")
    p2.foreach { run =>
      require(run.total.number("planned") == 960, "P2 must retain its separate fixed 960-trial pilot")
      require(run.leaves.map(_("task")).toSet == Set("ast_to_source", "source_to_ast") &&
        run.leaves.forall(_("view") == "complete"), "P2 must preserve both whole-program directions")
      require(protocolScalar(run.protocol, "sourceHash") == p2MockSourceHash &&
        protocolScalar(run.protocol, "protocolHash") == p2LiveProtocolHash,
        "P2 graded report must match the fixed source and protocol identities")
      require(Files.readString(input.resolve("p2/report.md"), UTF_8).contains("synthetic_mock=false"),
        "P2 live results must be explicitly non-synthetic; keep mock results in mock-p2")
      require(protocolScalar(run.protocol, "phase") == "p2", "P2 live must retain its separate protocol phase")
    }
    require(pilot.wholeStyle("ast_to_source", "complete", "named_end").number("correct") == 71,
      "Unexpected original P1 generation result")
    val namedGeneration = pilot.wholeStyle("ast_to_source", "complete", "named_end")
    require(namedGeneration.number("invalid_generated_syntax") == 0 &&
      namedGeneration.number("valid_syntax_wrong_ast") == 1 && namedGeneration.number("syntax_evaluable_trials") == 72,
      "Unexpected original P1 named-end syntax/AST result")
    require(pilot.sum("ast_to_source", "complete", "named_end", "8", "correct") == 24 &&
      pilot.sum("ast_to_source", "complete", "generic_end", "8", "correct") == 0,
      "Unexpected original P1 depth-8 result")
    val p2MockVerified = readP2MockWitness(input)
    val artifacts = publicFiles(input)
    Files.createDirectories(output)
    val copiedData = output.resolve("data")
    Files.createDirectories(copiedData)
    artifacts.foreach { relative =>
      val target = copiedData.resolve(relative).normalize()
      require(target.startsWith(copiedData), "Artifact path escaped the output data directory")
      Files.createDirectories(target.getParent)
      Files.copy(input.resolve(relative), target, StandardCopyOption.REPLACE_EXISTING)
      require(sha256(input.resolve(relative)) == sha256(target), s"Copied artifact hash mismatch: $relative")
    }
    val hashes = artifacts.map(p => p.toString.replace('\\', '/') -> sha256(input.resolve(p)))
    val fingerprint = hashText(hashes.map((name, hash) => s"$name\t$hash\n").mkString)
    val workflowUrl = sys.env.get("SYNTAX_REPORT_WORKFLOW_URL").filter(_.nonEmpty)
    workflowUrl.foreach(url => require(url.matches("https://github\\.com/[^/]+/[^/]+/actions/runs/[0-9]+(?:/attempts/[0-9]+)?"),
      "SYNTAX_REPORT_WORKFLOW_URL must identify a GitHub Actions workflow run"))
    val p2Verified = sys.env.get("SYNTAX_P2_TEST_VERIFIED").contains("true")
    require(!p2Verified || workflowUrl.nonEmpty, "Verified P2 test status requires its workflow-run evidence URL")
    val p2LiveCountsBasis = if p2.nonEmpty then "graded_report" else if p2Execution.nonEmpty then "execution_snapshot" else "no_saved_live_counts"
    val buildProvenance = s"""{"schemaVersion":1,"p2PostChangeFullTestVerified":$p2Verified,"workflowRunUrl":${workflowUrl.map(js).getOrElse("null")},"p2StandaloneMockVerified":$p2MockVerified,"p2MockWitness":${if p2MockVerified then js("data/" + p2MockWitnessPath) else "null"},"p2MockWitnessSha256":${if p2MockVerified then js(p2MockWitnessSha256) else "null"},"p2MockSourceHash":${if p2MockVerified then js(p2MockSourceHash) else "null"},"p2MockWorkflowRunUrl":${if p2MockVerified then js(p2MockWorkflowRunUrl) else "null"},"p2LiveCountsBasis":${js(p2LiveCountsBasis)},"p2LiveReportDispatched":${p2.map(_.total.number("dispatched").toString).getOrElse("null")},"p2LiveReportTerminalDispositions":${p2.map(_.total.number("terminal").toString).getOrElse("null")},"p2LiveExecutionDistinctDispatched":${p2Execution.map(_.distinctDispatched.toString).getOrElse("null")},"p2LiveExecutionTerminalDispositions":${p2Execution.map(_.terminal.toString).getOrElse("null")},"p2LiveGenerations":${p2.map(_.total.number("dispatched")).orElse(p2Execution.map(_.distinctDispatched)).getOrElse(0L)},"p2LiveTerminalDispositions":${p2.map(_.total.number("terminal")).orElse(p2Execution.map(_.terminal)).getOrElse(0L)},"p2LivePlannedTrials":${p2.map(_.total.number("planned")).orElse(p2Execution.map(_.planned)).getOrElse(960L)},"p2LiveReportAvailable":${p2.nonEmpty},"p2LiveExecutionStatusAvailable":${p2Execution.nonEmpty},"p2LiveExecutionSnapshotAt":${p2Execution.map(s => js(s.capturedAt)).getOrElse("null")},"p2LiveExecutionStatusPath":${if p2Execution.nonEmpty then js("data/" + p2LiveStatusPath) else "null"},"p2LiveExecutionStatusSha256":${p2Execution.map(s => js(s.sha256)).getOrElse("null")},"inputManifestSha256":${js(fingerprint)},"note":"The full-test flag is supplied by the publication workflow after its required full-test step. Standalone P2 mock status requires its fixed saved witness. P2 live count basis is explicit: report counts take precedence, while separately named execution counts and timestamp identify the saved heartbeat. These artifacts can represent different snapshots. Execution snapshots measure dispatch/terminal/accounting progress only, never model accuracy or CI. Synthetic wiring verification is not model measurement."}
"""
    Files.writeString(output.resolve("build-provenance.json"), buildProvenance, UTF_8)
    val html = render(pilot, main, p2, p2Execution, hashes, fingerprint, input, p2Verified, workflowUrl, p2MockVerified)
    Files.writeString(output.resolve("index.html"), html, UTF_8)
    println(s"Generated ${output.resolve("index.html")} from ${artifacts.size} saved public artifacts")
    println(s"Input manifest SHA-256: $fingerprint")
    println(s"P1: ${pilot.total.number("correct")}/${pilot.total.number("model_evaluable")}; " +
      s"main: ${main.total.number("terminal")}/${main.total.number("planned")} terminal dispositions")

  private def readRun(root: Path, name: String): Run =
    val dir = root.resolve(name)
    requiredFiles.foreach(f => require(Files.isRegularFile(dir.resolve(f), LinkOption.NOFOLLOW_LINKS), s"Missing $name/$f"))
    Run(name, readCsv(dir.resolve("summary.csv")), readCsv(dir.resolve("paired-comparisons.csv")),
      readCsv(dir.resolve("errors.csv")), Files.readString(dir.resolve("protocol.json"), UTF_8))

  /** A heartbeat records operational progress only; it is never a grading/statistics input. */
  private def readP2ExecutionSnapshot(root: Path): Option[ExecutionSnapshot] =
    import ExecutionJson.*
    val path = root.resolve(p2LiveStatusPath)
    if !Files.exists(path, LinkOption.NOFOLLOW_LINKS) then None
    else
      require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS), "P2 live status must be a regular file")
      val fields = new ExecutionJsonReader(Files.readString(path, UTF_8)).read() match
        case Object(values) => values
        case _ => throw IllegalArgumentException("P2 execution snapshot must be a JSON object")
      val required = Set("capturedAt", "utcWindow", "planned", "batchLimit", "tokenCountAttempts",
        "generationDispatchAttempts", "distinctDispatched", "terminal", "infrastructureTerminalCount",
        "statuses", "actualKnownTokens", "openReservedTokens", "unresolvedIncludesCurrentInFlight",
        "cumulativeCap", "synthetic_mock", "sourceHash", "protocolHash", "requestedModel", "phase", "runLabel", "measurementScope")
      require(fields.keySet == required, "P2 execution snapshot has missing or unsupported fields")
      def text(key: String): String = fields(key) match
        case Text(value) => value
        case _ => throw IllegalArgumentException(s"Expected snapshot string: $key")
      def number(key: String): Long = fields(key) match
        case Number(value) => value
        case _ => throw IllegalArgumentException(s"Expected snapshot non-negative integer: $key")
      def bool(key: String): Boolean = fields(key) match
        case Bool(value) => value
        case _ => throw IllegalArgumentException(s"Expected snapshot boolean: $key")
      require(!bool("synthetic_mock"), "Synthetic heartbeat must not establish live P2 execution")
      require(text("sourceHash") == p2MockSourceHash && text("protocolHash") == p2LiveProtocolHash &&
        text("requestedModel") == "gpt-5.6-terra" && text("phase") == "p2" &&
        text("runLabel") == "full-program-pilot-live-v1", "Unexpected P2 live snapshot source/protocol/run identity")
      require(text("measurementScope") == "Execution progress only; not strict grades, accuracy, or inferential statistics.",
        "P2 execution snapshot must preserve its operational measurement scope")
      val capturedAt = text("capturedAt")
      val instant = java.time.Instant.parse(capturedAt)
      require(capturedAt.endsWith("Z") && instant.atOffset(java.time.ZoneOffset.UTC).toLocalDate.toString == text("utcWindow"),
        "P2 snapshot UTC window does not match its capture timestamp")
      val planned = number("planned")
      val dispatched = number("distinctDispatched")
      val terminal = number("terminal")
      require(planned == 960 && dispatched > 0 && dispatched <= planned && terminal <= dispatched,
        "Invalid P2 execution planned/dispatched/terminal counts")
      require(number("batchLimit") > 0 && number("batchLimit") <= planned &&
        number("generationDispatchAttempts") >= dispatched && number("tokenCountAttempts") >= dispatched &&
        number("infrastructureTerminalCount") <= terminal, "Invalid P2 execution attempt/infrastructure counts")
      val statuses = fields("statuses") match
        case Array(values) => values.map {
          case Object(entry) if entry.keySet == Set("count", "status") =>
            val status = entry("status") match
              case Text(value) if value.matches("[a-z][a-z0-9_]*") => value
              case _ => throw IllegalArgumentException("Invalid P2 execution status label")
            val count = entry("count") match
              case Number(value) if value > 0 && value <= terminal => value
              case _ => throw IllegalArgumentException("Invalid P2 execution status count")
            status -> count
          case _ => throw IllegalArgumentException("Malformed P2 execution status entry")
        }
        case _ => throw IllegalArgumentException("P2 execution statuses must be an array")
      require(statuses.map(_._1).distinct.size == statuses.size && statuses.map(_._2).sum == terminal,
        "P2 execution statuses must partition terminal dispositions without duplicates")
      val known = number("actualKnownTokens")
      val reserved = number("openReservedTokens")
      val cap = number("cumulativeCap")
      require(cap > 0 && known <= cap && reserved <= cap - known, "P2 execution accounting exceeds its cumulative cap")
      Some(ExecutionSnapshot(capturedAt, text("utcWindow"), planned, dispatched, terminal, known, reserved,
        bool("unresolvedIncludesCurrentInFlight"), sha256(path)))

  /** Only the fixed, saved witness can establish completion; changed or malformed bytes fail closed. */
  private def readP2MockWitness(root: Path): Boolean =
    val path = root.resolve(p2MockWitnessPath)
    if !Files.exists(path, LinkOption.NOFOLLOW_LINKS) then false
    else
      require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS), "P2 mock witness must be a regular file")
      require(sha256(path) == p2MockWitnessSha256, "Unexpected P2 mock witness bytes; do not publish changed claims")
      val text = Files.readString(path, UTF_8)
      require(protocolScalar(text, "sourceHash") == p2MockSourceHash, "Unexpected P2 mock source identity")
      require(protocolScalar(text, "workflowRunUrl") == p2MockWorkflowRunUrl, "Unexpected P2 mock workflow identity")
      require(protocolScalar(text, "externalHttpEvidence") ==
        "Explicit --mock without --execute uses MockClient; code-path evidence, not packet capture.",
        "Unexpected P2 external-HTTP evidence scope")
      val expected = Vector("schemaVersion" -> "1", "synthetic_mock" -> "true", "planned" -> "960",
        "families" -> "48", "terminal" -> "960", "strictCorrect" -> "960", "simulatedAdapterAttempts" -> "1920",
        "externalHttpCalls" -> "0", "reportFilesByteIdentical" -> "true", "resumeAdditionalLogicalTrials" -> "0",
        "resumeAdditionalAdapterAttempts" -> "0", "unresolvedReservedTokens" -> "0", "liveGenerations" -> "0",
        "immutableArtifactsUnchanged" -> "true", "checksPassed" -> "true")
      expected.foreach { (key, value) =>
        val pattern = ("\\\"" + java.util.regex.Pattern.quote(key) +
          "\\\"\\s*:\\s*(true|false|0|[1-9][0-9]*)(?=\\s*[,}])").r
        val found = pattern.findAllMatchIn(text).map(_.group(1)).toVector
        require(found == Vector(value), s"Unexpected or missing P2 mock witness field $key")
      }
      Vector("report.md", "summary.csv", "paired-comparisons.csv", "errors.csv", "scores.jsonl").foreach { name =>
        require(Files.isRegularFile(root.resolve(s"mock-p2/$name"), LinkOption.NOFOLLOW_LINKS),
          s"Missing separate saved P2 mock report: $name")
      }
      require(Files.readString(root.resolve("mock-p2/report.md"), UTF_8).contains("synthetic_mock=true"),
        "Saved P2 mock report must retain its synthetic label")
      true

  private def publicFiles(root: Path): Vector[Path] =
    val stream = Files.walk(root)
    try
      val all = stream.iterator().asScala.toVector
      require(!all.exists(Files.isSymbolicLink(_)), "Public data may not contain symbolic links")
      val files = all.filter(p => Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)).map(root.relativize(_))
      require(!files.exists(p => p.iterator().asScala.exists(_.toString.startsWith("."))), "Hidden public artifacts are forbidden")
      files.sortBy(_.toString.replace('\\', '/'))
    finally stream.close()

  /** Strict RFC-style CSV handling, including embedded line breaks and doubled quotes. */
  private def readCsv(path: Path): Vector[Row] =
    val raw = Files.readString(path, UTF_8).stripPrefix("\ufeff")
    val rows = Vector.newBuilder[Vector[String]]
    val fields = Vector.newBuilder[String]
    val field = new StringBuilder
    var state = 0 // 0: unquoted, 1: quoted, 2: closed quote
    var i = 0
    var touched = false
    var rowFields = 0
    def endField(): Unit =
      fields += field.toString
      field.clear()
      state = 0
      rowFields += 1
    def endRow(): Unit =
      endField()
      rows += fields.result()
      fields.clear()
      touched = false
      rowFields = 0
    while i < raw.length do
      val c = raw.charAt(i)
      if state == 1 then
        if c == '"' then
          if i + 1 < raw.length && raw.charAt(i + 1) == '"' then
            field.append('"')
            i += 1
          else state = 2
        else field.append(c)
        touched = true
      else
        c match
          case '"' =>
            require(state == 0 && field.isEmpty, s"Unexpected CSV quote in $path at character $i")
            state = 1
            touched = true
          case ',' => endField(); touched = true
          case '\r' | '\n' =>
            endRow()
            if c == '\r' && i + 1 < raw.length && raw.charAt(i + 1) == '\n' then i += 1
          case _ =>
            require(state != 2, s"Characters after closing CSV quote in $path at character $i")
            field.append(c)
            touched = true
      i += 1
    require(state != 1, s"Unclosed CSV quote in $path")
    if touched || field.nonEmpty || rowFields > 0 then endRow()
    val parsed = rows.result()
    require(parsed.nonEmpty && parsed.head.nonEmpty && parsed.head.forall(_.nonEmpty), s"Missing CSV header in $path")
    val header = parsed.head
    require(header.distinct.size == header.size, s"Duplicate CSV header in $path")
    parsed.tail.zipWithIndex.map { (row, index) =>
      require(row.size == header.size, s"CSV row ${index + 2} has ${row.size} columns; expected ${header.size} in $path")
      Row(header.zip(row).toMap)
    }

  private def sha256(path: Path): String =
    val digest = MessageDigest.getInstance("SHA-256")
    val stream = Files.newInputStream(path)
    try
      val buffer = new Array[Byte](65536)
      var count = stream.read(buffer)
      while count >= 0 do
        if count > 0 then digest.update(buffer, 0, count)
        count = stream.read(buffer)
    finally stream.close()
    digest.digest().map(b => f"${b & 0xff}%02x").mkString

  private def hashText(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.getBytes(UTF_8)).map(b => f"${b & 0xff}%02x").mkString
  private def h(value: String): String = value.flatMap {
    case '&' => "&amp;"
    case '<' => "&lt;"
    case '>' => "&gt;"
    case '"' => "&quot;"
    case '\'' => "&#39;"
    case c => c.toString
  }
  private def js(value: String): String = "\"" + value.flatMap {
    case '"' => "\\\""
    case '\\' => "\\\\"
    case '\n' => "\\n"
    case '\r' => "\\r"
    case '\t' => "\\t"
    case '<' => "\\u003c"
    case '&' => "\\u0026"
    case c if c < ' ' || c == '\u2028' || c == '\u2029' => f"\\u${c.toInt}%04x"
    case c => c.toString
  } + "\""
  private def comma(value: Long): String = value.toString.reverse.grouped(3).mkString(",").reverse
  private def percent(correct: Long, evaluable: Long): String =
    if evaluable == 0 then "NA" else
      String.format(java.util.Locale.ROOT, "%.3f%%", Double.box(correct.toDouble * 100.0 / evaluable))
  private def link(path: String, label: String): String = s"<a href=\"data/${h(path)}\" download>${h(label)}</a>"
  private def source(run: String, file: String, label: String): String = link(s"$run/$file", label)
  private def detail(title: String, body: String): String = s"<details><summary>${h(title)}</summary>$body</details>"
  private def protocolScalar(protocol: String, key: String): String =
    val pattern = ("\\\"" + java.util.regex.Pattern.quote(key) + "\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").r
    val values = pattern.findAllMatchIn(protocol).map(_.group(1)).toVector
    require(values.size == 1, s"Expected exactly one protocol string field $key")
    values.head

  private def render(pilot: Run, main: Run, p2: Option[Run], p2Execution: Option[ExecutionSnapshot], hashes: Vector[(String, String)],
    fingerprint: String, input: Path, p2Verified: Boolean, workflowUrl: Option[String], p2MockVerified: Boolean): String =
    val named = pilot.wholeStyle("ast_to_source", "complete", "named_end")
    val typed = pilot.wholeStyle("ast_to_source", "complete", "typed_end")
    val generic = pilot.wholeStyle("ast_to_source", "complete", "generic_end")
    val runs = Vector(pilot, main) ++ p2.toVector
    val terminal = main.total.number("terminal")
    val planned = main.total.number("planned")
    val mainComplete = terminal == planned
    def runState(run: Run): String =
      if run.total.number("terminal") == run.total.number("planned") then "全計画試行の処置を保存済み" else "途中の保存済みスナップショット"
    def counts(run: Run): String =
      s"計画 ${comma(run.total.number("planned"))}、送信 ${comma(run.total.number("dispatched"))}、" +
        s"処置保存 ${comma(run.total.number("terminal"))}、評価可能 ${comma(run.total.number("model_evaluable"))}、" +
        s"正解 ${comma(run.total.number("correct"))}。infrastructure missing ${comma(run.total.number("infrastructure_missing"))}、" +
        s"未送信 ${comma(run.total.number("not_dispatched"))}、incomplete ${comma(run.total.number("incomplete"))}、refusal ${comma(run.total.number("refusal"))}。"
    val mainCiStates = main.comparisons.map(_("ci_status")).distinct.sorted.mkString(" / ")
    val mainNotice = s"${runState(main)}。${counts(main)}CSV の CI status: $mainCiStates。"
    val interruptionPath = "supporting/live-interruption.txt"
    val interruptionNotice =
      if Files.isRegularFile(input.resolve(interruptionPath)) then
        val note = Files.readString(input.resolve(interruptionPath), UTF_8).trim
        require(note.nonEmpty && note.length <= 4000, "Invalid saved execution interruption note")
        s"<aside class=\"callout\"><strong>実行の中断と途中結果。</strong><p>${h(note)}</p>${link(interruptionPath, "保存済み中断記録")}</aside>"
      else ""
    val supportRecovery = "supporting/main-zero-http-recovery.json"
    val historicalRecovery =
      if Files.isRegularFile(input.resolve(supportRecovery)) then
        s"初回中断時の結果不明の応答と未解決使用量を保持した記録: ${link(supportRecovery, "初回の復旧証跡")}。"
      else "未解決使用量はゼロに置き換えず、保存済み report の会計記録に従う。"
    val currentRecovery = "supporting/main-count-only-recovery-v2.json"
    val reservation = historicalRecovery +
      (if Files.isRegularFile(input.resolve(currentRecovery)) then
        s" 記録ファイルの競合で送信前に止まった試行は、外部通信なしの復旧処理で未送信として残した。既存の応答と予約を保持した ${link(currentRecovery, "今回の復旧証跡")}。"
      else "")
    val p2ImplementationText =
      if p2MockVerified then
        s"実装と通し mock 配線検証を完了。960/960 の synthetic strict-correct はモデルの測定に含めない。" +
          s"${link(p2MockWitnessPath, "保存済み mock 証跡")} / ${link("mock-p2/report.md", "synthetic_mock=true の別 report")}。"
      else if p2Verified then
        s"変更後の全テストが公開 workflow の先行工程で成功。<a href=\"${h(workflowUrl.get)}\">実際の workflow</a>。これは実装の検証。"
      else "実装の検証状態は保存資料と build-provenance.json を参照。モデル測定の有無とは別に記録する。"
    val p2ExecutionText = p2Execution.map { snapshot =>
      s"<strong>live実行開始済み・進捗snapshot</strong>。保存時刻 <time datetime=\"${h(snapshot.capturedAt)}\">${h(snapshot.capturedAt)}</time> " +
        s"（UTC window ${h(snapshot.utcWindow)}）。計画 ${comma(snapshot.planned)}、distinct送信 ${comma(snapshot.distinctDispatched)}、" +
        s"処置保存 ${comma(snapshot.terminal)}。既知使用量 ${comma(snapshot.actualKnownTokens)} tokens、" +
        s"未解決予約 ${comma(snapshot.openReservedTokens)} tokens" +
        (if snapshot.unresolvedIncludesCurrentInFlight then "（実行中の要求を含む）" else "") +
        s"。${link(p2LiveStatusPath, "保存済みの非synthetic進捗証跡")}。" +
        "送信・処置・会計の進捗であり、completedは厳密正解を意味しない。リアルタイム表示ではない。"
    }
    val p2StatusText = p2.map(run => s"${runState(run)}。${counts(run)}${source("p2", "report.md", "保存済み live P2 report")}")
      .getOrElse(p2ExecutionText.getOrElse("公開資料にlive P2の実行記録はない。モデルの成功率・差・信頼区間は未掲載。"))
    val p2ProgressAfterReport = if p2.nonEmpty then p2ExecutionText.map(text => s"<p>$text</p>").getOrElse("") else ""
    val dataJs = runs.map { run =>
      val keys = dimensions ++ additive
      val rows = run.leaves.map(row => keys.map(k => js(k) + ":" + js(row(k))).mkString("{", ",", "}")).mkString("[", ",", "]")
      js(run.name) + ":" + rows
    }.mkString("{", ",", "}")
    val runLabels = runs.map { run =>
      val title = run.name match
        case "pilot" => "P1 exploratory pilot"
        case "main" => "元の frozen reading main"
        case "p2" => "P2 exploratory pilot"
      js(run.name) + ":" + js(s"$title · ${runState(run)}")
    }.mkString("{", ",", "}")
    val runOptions = runs.map { run =>
      val title = run.name match
        case "pilot" => "P1 pilot · 生成＋補助読み取り"
        case "main" => "元の reading main · 別実験"
        case "p2" => "P2 pilot · 生成＋全文 parsing"
      s"<option value=\"${h(run.name)}\">${h(title)}</option>"
    }.mkString
    val artifacts = hashes.map { (path, hash) =>
      s"<tr><td>${link(path, path)}</td><td><code class=\"hash\">$hash</code></td></tr>"
    }.mkString
    val generationPairs = Vector("typed_end" -> "generic_end", "named_end" -> "generic_end",
      "named_end" -> "typed_end", "named_end" -> "padded_end")
    def comparisonRows(run: Run, task: String, view: String, pairs: Vector[(String, String)]): String =
      pairs.flatMap { (treatment, baseline) =>
        val found = run.comparisons.filter(r => r("task") == task && r("view") == view &&
          r("treatment") == treatment && r("baseline") == baseline)
        require(found.size <= 1, s"Duplicate saved contrast: ${run.name}/$task/$view/$treatment/$baseline")
        found.map { row =>
          val interval = if Set("computed", "degenerate").contains(row("ci_status")) then
            s"[${h(row("ci_lower_pp"))}, ${h(row("ci_upper_pp"))}]" else "未計算"
          val difference = row("difference_percentage_points")
          val displayedDifference = if difference.matches("[0-9].*") && difference != "0.000" then "+" + difference else difference
          s"<tr><th scope=\"row\">${h(labels(treatment).take(1))}−${h(labels(baseline).take(1))}</th>" +
            s"<td>${h(row("matched_families_available"))}/${h(row("matched_families_planned"))}</td>" +
            s"<td>${h(displayedDifference)} pp</td><td>$interval</td><td>${h(row("ci_status"))}</td>" +
            s"<td>${h(row("classification"))}</td></tr>"
        }
      }.mkString
    def comparisonTable(run: Run, task: String, view: String, pairs: Vector[(String, String)], title: String): String =
      s"<div class=\"table-scroll\"><table><caption>${h(title)}</caption><thead><tr><th scope=\"col\">比較</th>" +
        "<th scope=\"col\">対応 family / 計画</th><th scope=\"col\">正解率の差</th><th scope=\"col\">保存済み95% CI</th>" +
        "<th scope=\"col\">CI status</th><th scope=\"col\">計画上の分類</th></tr></thead>" +
        s"<tbody>${comparisonRows(run, task, view, pairs)}</tbody></table></div>"
    def generationTable(run: Run, title: String): String =
      val rows = styles.map { style =>
        val row = run.wholeStyle("ast_to_source", "complete", style)
        s"<tr${if style == "named_end" || style == "typed_end" then " class=\"named-row\"" else ""}>" +
          s"<th scope=\"row\">${h(labels(style))}</th><td>${row.number("correct")}/${row.number("model_evaluable")}</td>" +
          s"<td>${percent(row.number("correct"), row.number("model_evaluable"))}</td>" +
          s"<td>${row.number("invalid_generated_syntax")}</td><td>${row.number("valid_syntax_wrong_ast")}</td>" +
          s"<td>${comma(row.number("output_tokens_known"))} / ${row.number("output_usage_known_trials")}応答</td></tr>"
      }.mkString
      s"<div class=\"table-scroll\"><table><caption>${h(title)}</caption><thead><tr><th scope=\"col\">表記</th>" +
        "<th scope=\"col\">正解 / 評価可能</th><th scope=\"col\">厳密正解率</th><th scope=\"col\">構文不成立</th>" +
        "<th scope=\"col\">構文成立・AST不一致</th><th scope=\"col\">既知の出力tokens / 使用量既知の応答数</th></tr></thead>" +
        s"<tbody>$rows</tbody></table></div>"
    def depthTable(run: Run, depths: Vector[String], title: String): String =
      val rows = styles.map { style =>
        val cells = depths.map { depth =>
          val correct = run.sum("ast_to_source", "complete", style, depth, "correct")
          val evaluable = run.sum("ast_to_source", "complete", style, depth, "model_evaluable")
          s"<td${if depth == depths.last then " class=\"focus-cell\"" else ""}>$correct/$evaluable</td>"
        }.mkString
        s"<tr><th scope=\"row\">${h(labels(style))}</th>$cells</tr>"
      }.mkString
      s"<div class=\"table-scroll\"><table><caption>${h(title)}</caption><thead><tr><th scope=\"col\">表記</th>" +
        depths.map(d => s"<th scope=\"col\">深さ $d</th>").mkString + s"</tr></thead><tbody>$rows</tbody></table></div>"
    val initialRows = styles.map { style =>
      val row = pilot.wholeStyle("ast_to_source", "complete", style)
      tableRow(style, row.number("correct"), row.number("model_evaluable"), row.number("planned"),
        row.number("dispatched"), row.number("terminal"), row.number("infrastructure_missing"), row.number("not_dispatched"))
    }.mkString
    val syntax = styles.map { style =>
      val opener = if style == "braces" then "func n_abcdefgh {" else "func n_abcdefgh"
      val closer = style match
        case "braces" => "}"
        case "generic_end" => "end"
        case "typed_end" => "end func"
        case "named_end" => "end func n_abcdefgh"
        case "padded_end" => "end junk z_aaaaaaaa"
      val explanation = style match
        case "braces" => "記号で区切る補助 baseline"
        case "generic_end" => "全ブロックで共通の終端"
        case "typed_end" => "閉じる種類を繰り返す"
        case "named_end" => "閉じる種類と名前を繰り返す"
        case "padded_end" => "意味を持たない固定suffixで長さを増す"
      s"<article class=\"syntax-card\"><h3>${h(labels(style))}</h3><p class=\"muted\">${h(explanation)}</p>" +
        s"<pre><code>${h(opener + "\nlet x_a 1739\n" + closer)}</code></pre></article>"
    }.mkString
    val protocolRows = runs.map { run =>
      val name = if run.name == "pilot" then "P1 exploratory pilot" else if run.name == "main" then "Original frozen reading main" else "P2 exploratory pilot"
      s"<tr><th scope=\"row\">${h(name)}</th><td><code>${h(protocolScalar(run.protocol, "protocolHash"))}</code></td>" +
        s"<td><code>${h(protocolScalar(run.protocol, "sourceHash"))}</code></td><td>${source(run.name, "protocol.json", "protocol.json")}</td></tr>"
    }.mkString
    val promptLink = "supporting/pilot-named-end-ast-to-source-prompt.txt"
    val prompt =
      if Files.isRegularFile(input.resolve(promptLink)) then
        val raw = Files.readString(input.resolve(promptLink), UTF_8)
        val grammar = raw.split("\nSYNTAX\n", 2).lift(1).flatMap(_.split("\nEXAMPLES\n", 2).headOption)
        grammar.map(g => detail("実際の D / natural の文法説明", s"<pre><code>${h(g.trim)}</code></pre>"))
          .getOrElse("") + s"<p>${link(promptLink, "文法・固定8例・テストを含む保存済み全文prompt")} / " +
          s"${link("supporting/pilot-named-end-ast-to-source-request.json", "保存済みcanonical logical request")}</p>"
      else "<p>文法と固定8例は保存済み protocol / report を参照。</p>"
    val p2Generation = p2.map { run =>
      s"<h3>別プロトコルの P2：深さ16を加えた生成。</h3><p>${h(runState(run))}。" +
        "48 structural families、深さ2/4/8/16 × filler 0/8/32、各セル4 family。" +
        "生成側の計画は各表記96応答で、P1と合算しない。</p>" +
        generationTable(run, "P2 live · ast_to_source / complete · natural + nonce") +
        depthTable(run, Vector("2", "4", "8", "16"), "P2 live · 深さ別の厳密生成正解 / 評価可能") +
        comparisonTable(run, "ast_to_source", "complete", generationPairs, "P2 live · 保存済み探索的生成比較（pp）") +
        s"<p>成功率・差・CI は ${source("p2", "summary.csv", "summary.csv")} / " +
        s"${source("p2", "paired-comparisons.csv", "paired-comparisons.csv")} に基づく。" +
        "P2は追加の探索的pilotであり、生成仮説のconfirmatory mainには読み替えない。</p>"
    }.getOrElse("<h3>P2：より深い生成を、別のpilotで調べる。</h3><p>深さ16を加えた48 familyの固定計画。" +
      "生成480応答と全文parsing480応答、計960応答。</p>" +
      p2ExecutionText.map(text => s"<p>$text</p><p>実測は開始済み。採点済みレポートが未掲載のため、進捗snapshotから正解率・差・CIを作らない。</p>")
        .getOrElse("<p>公開資料にはlive実行記録と採点済みレポートがなく、成功率・差・CIを表示しない。</p>") +
      "<p>mockの960/960は配線検証で、モデルの成績ではない。</p>")
    val p2Parsing = p2.map { run =>
      val rows = styles.map { style =>
        val row = run.wholeStyle("source_to_ast", "complete", style)
        s"<tr><th scope=\"row\">${h(labels(style))}</th><td>${row.number("correct")}/${row.number("model_evaluable")}</td>" +
          s"<td>${percent(row.number("correct"), row.number("model_evaluable"))}</td></tr>"
      }.mkString
      "<div class=\"table-scroll\"><table><caption>P2 live · 全文から中立JSON ASTを復元する補助課題</caption>" +
        "<thead><tr><th scope=\"col\">表記</th><th scope=\"col\">正解 / 評価可能</th><th scope=\"col\">厳密正解率</th></tr></thead>" +
        s"<tbody>$rows</tbody></table></div>" +
        comparisonTable(run, "source_to_ast", "complete", generationPairs, "P2 live · 補助parsingの保存済み探索的比較（pp）")
    }.getOrElse("<p>全文parsingのlive P2成績は採点済み成果物が未掲載。進捗snapshot、手作業の例題、synthetic mockをモデルの正解率には数えない。</p>")
    val mainComparison = comparisonTable(main, "scope_lookup", "after_close", Vector("named_end" -> "generic_end"),
      "元の frozen reading main · 事前固定のT1 / after_close / D−B（pp）")
    val readingComparisons = Vector("scope_lookup" -> "変数値", "active_stack" -> "開いているブロック列").map { (task, title) =>
      s"<h3>P1の補助読み取り：${h(title)}</h3>" +
        comparisonTable(pilot, task, "after_close", Vector("named_end" -> "generic_end"), s"P1 · $task / after_close · 保存済み探索的比較（pp）")
    }.mkString

    s"""<!doctype html>
<html lang="ja">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="description" content="未知の言語を生成するとき、種類や名前を繰り返す冗長な閉じ構文は有利か。保存済みの厳密生成結果、補助読み取り、別の凍結済み読み取り実験を区別したレポート。">
<title>未知の言語の生成と、冗長な閉じ構文 — Syntax locality</title>
<style>$css</style>
</head>
<body>
<a class="skip" href="#question">本文へ</a>
<header class="hero"><div class="wrap">
<p class="eyebrow">SYNTAX LOCALITY / GENERATION HYPOTHESIS</p>
<h1>未知の言語を生成するとき、<br>冗長な閉じ方は有利か。</h1>
<p class="lead"><code>def foo ... end def</code>、あるいは <code>def foo ... end def foo</code>。閉じる場所に種類や名前を繰り返すことで、長距離の文脈に頼る必要を減らし、構造を保った生成をしやすくできるのではないか。</p>
<div class="hero-facts"><div><span class="big">${typed.number("correct")} / ${typed.number("model_evaluable")}</span><span>P1 · C：種類付き終端<br>${percent(typed.number("correct"), typed.number("model_evaluable"))} 厳密生成正解</span></div><div><span class="big">${named.number("correct")} / ${named.number("model_evaluable")}</span><span>P1 · D：種類＋名前付き終端<br>${percent(named.number("correct"), named.number("model_evaluable"))} 厳密生成正解</span></div><div><span class="big">${generic.number("correct")} / ${generic.number("model_evaluable")}</span><span>P1 · B：共通の end<br>${percent(generic.number("correct"), generic.number("model_evaluable"))} 厳密生成正解</span></div></div>
<p class="hero-source">出典: ${source("pilot", "summary.csv", "P1の保存済みsummary.csv")}。生成結果は探索的pilotの有限の観測。モデル内部の依存長は測っていない。</p>
</div></header>
<nav aria-label="レポートの章"><div class="wrap"><a href="#question">問いと仮説</a><a href="#syntax">比較する閉じ方</a><a href="#method">生成の採点</a><a href="#results">生成結果</a><a href="#interpretation">解釈と限界</a><a href="#adjunct">補助実験</a><a href="#status">状態と資料</a></div></nav>
<main class="wrap">
$interruptionNotice
<section id="question">
<p class="section-label">01 / QUESTION</p><h2>問いは、未知の言語を正しく「生成」できるか。</h2>
<p>今回確かめたいのは、<strong>「閉じるのに冗長だが長距離依存を必要としない構造が有利なのでは」</strong>という仮説。閉じ終わるたびに種類、または種類と名前を明示する構文が、共通の終端だけを使う構文より、元の構造を保った全文生成に向いているかを調べる。</p>
<div class="two-columns"><div><h3>種類を繰り返す</h3><pre><code>def foo
  ...
end def</code></pre><p>閉じる場所にも「def」が現れる。実験ではCに対応する考え方。</p></div><div><h3>種類と名前を繰り返す</h3><pre><code>def foo
  ...
end def foo</code></pre><p>閉じる対象をさらに具体的に示す。実験ではDに対応する考え方。</p></div></div>
<p class="muted">この <code>def / foo / ...</code> は仮説を説明する概念例。実際の実験文法・識別子ではなく、既存の特定言語の関数構文を比較しているわけでもない。</p>
<p>仮説の機構は、終端がその場所の構造的な手掛かりになること。今回直接測るのは<strong>入力ASTと完全に一致するソースを生成できた割合</strong>や失敗の種類であり、モデル内部の長距離依存そのものではない。Dで正しい名前を出力する際には、元の名前との対応を保つ必要も残る。この点を分けた上で、冗長な閉じ方の実用上の利点を検討する。</p>
<p>「未知」は、課題内で文法を説明して適用させる小言語という意味。説明と固定8例を渡すため、例だけから文法を発見する課題ではない。モデルの学習履歴に一切なかったことは確認できない。</p>
</section>
<section id="syntax">
<p class="section-label">02 / CONTROLLED COMPARISON</p><h2>同じ構造を、5つの閉じ方で表す。</h2>
<p>次は実際のnatural語彙を使う最小例。AST（木として表したプログラム）、識別子、値、文の順序は同じ。Aだけ開き行にも <code>{</code> が付き、主な違いはブロックの終端にある。</p>
<div class="syntax-grid">$syntax</div>
<div class="table-scroll"><table><caption>それぞれの比較で確かめたいこと</caption><thead><tr><th scope="col">比較</th><th scope="col">変える要素</th><th scope="col">問い</th></tr></thead><tbody><tr><th scope="row">C−B</th><td>終端に種類を追加</td><td>種類の冗長性が生成を助けるか</td></tr><tr><th scope="row">D−C</th><td>終端に名前も追加</td><td>対象の名前まで明示する追加効果があるか</td></tr><tr><th scope="row">D−B</th><td>種類と名前を追加</td><td>具体的な終端全体の利点はどれくらいか</td></tr><tr><th scope="row">D−E</th><td>長い終端に構造情報を持たせる</td><td>単に長くするだけで同じ利点が出るか</td></tr></tbody></table></div>
<p>Eの <code>junk z_aaaaaaaa</code> はどのブロックでも同じ定数で、種類・名前・深さを表さない。基本条件のDとEの終端は同じ文字数になるが、同じtoken数とは限らない。長さの対照として使い、完全なtoken長の統制とは呼ばない。</p>
<p>対応する5条件で、AST・名前・変数・値・意味上の順序・few-shotの内容と順序・語彙対応を保持する。<code>unit / func / area</code> はいずれも単にローカルスコープを開く。同じ意味を異なる種類の語で表すため、実言語の関数呼び出し等は含めない。</p>
<p>naturalとnonceの2語彙条件がある。nonceでは予約語を固定8セットの無意味語に置換し、同じfamilyの5表記に同じ対応を使う。主な集計ではnatural/nonceを等重みで扱う。これは既知のキーワードへの依存を弱める工夫であり、事前学習やtokenizationの影響を消すものではない。</p>
</section>
<section id="method">
<p class="section-label">03 / GENERATION AND GRADING</p><h2>入力は中立な木の表。正解は、全文が同じ木に戻ること。</h2>
<div class="flow"><span>親・順序を記したAST表</span><span aria-hidden="true">→</span><span>指定の文法で全文生成</span><span aria-hidden="true">→</span><span>構文検査</span><span aria-hidden="true">→</span><span>元のASTと完全一致</span></div>
<p><code>ast_to_source</code> では、各nodeの親・兄弟内の順序・種類・名前・変数・値・payloadを表で渡す。入れ子の波括弧のコピーだけでAを生成できる入力にはしない。ただし、この親子関係の表自体も構造的な手掛かりを含む。</p>
<div class="two-columns"><div><h3>厳密正解の条件</h3><p>応答全体を対象表記のparserで受理でき、そのASTが元のASTと一致すること。ブロックのkindとname、親子関係、文順、変数名、値、nop payloadを比較する。構文が成立しても、別の木なら不正解。</p><p>説明文やMarkdown fenceはstrict scoreで失敗。incomplete / refusalも評価可能なモデル失敗に含める。API拒否・通信失敗・結果不明等はinfrastructure missingとして分け、未送信も別に残す。誤答の再試行やbest-of-Nは使わない。</p></div><div><h3>試行と分母</h3><p>P1生成は36 structural families、深さ2/4/8 × filler 0/8/32、各セル4 family。同じfamilyをnatural/nonceと5表記で生成し、各表記72応答。独立した構造の標本は36であり、72や360を独立AST数とは数えない。</p><p>モデルは <code>${h(protocolScalar(pilot.protocol, "model"))}</code>、reasoning effortは <code>${h(protocolScalar(pilot.protocol, "reasoningEffort"))}</code>。固定8例、生成の共通output capは16,384。P1は深さ8・filler32までで、巨大な読み取りprefixとは別の難易度。</p></div></div>
<p>goldはlive要求から構造的に分離して保存する。再帰AST oracleと明示スタックevent oracleは独立に実装し、監査で5表記の一致を確認する。有限のテスト成功は、全入力の正しさの数学的証明ではない。${link("docs/protocol.md", "採点とprotocol")} / ${link("docs/limitations.md", "limitations")}</p>
$prompt
</section>
<section id="results">
<p class="section-label">04 / GENERATION RESULTS</p><h2>P1では、種類付きと名前付きの終端が生成に有利やった。</h2>
<aside class="callout"><strong>このpilotで確認できたこと。</strong>Cは60/72、Dは71/72で、Bの46/72を上回った。情報を持たない長い終端Eは49/72。したがって、この条件の有限の観測では、終端の種類や名前を繰り返す構文に生成上の利点が見られた。</aside>
${generationTable(pilot, "P1 · ast_to_source / complete · 各表記72応答、36 structural families")}
<p>Dの72応答はすべて構文として成立し、71件が元のASTと一致した。Cは11件が構文不成立、1件が構文成立後のAST不一致。文字数を増すだけのEは、Dほどの改善を示さなかった。出力token数は表記で異なるため、正解率と併記した。${source("pilot", "summary.csv", "元のsummary.csv")}</p>
<h3>深いネストで差が大きい。</h3>
${depthTable(pilot, Vector("2", "4", "8"), "P1 · 深さ別の厳密生成正解 / 評価可能 · 各セル24応答（12 family）")}
<p>深さ8ではBが0/24、Cが12/24、Dが24/24、Eが1/24。Cの種類ラベルにも利点が見られ、Dの名前ラベルを加えた条件はさらに高かった。一方、深さ2ではDに1件のAST不一致があり、すべての個別問題でDが勝つという結果ではない。</p>
<p>深さ8のB/Eはそれぞれ23件で、parserの最初のエラーが <code>unclosed_scope</code>（EOFでもスコープが開いている）だった。P1生成の73件の構文不成立はすべてprovider statusがcompletedで、outputは151–764 tokens、共通capは16,384。単純な出力上限打ち切りとは区別する。最初のエラーは全欠陥の網羅的な数ではない。${link("docs/pilot-interpretation.md", "保存済み失敗分析")}</p>
<h3>保存済みの対応付き比較。</h3>
${comparisonTable(pilot, "ast_to_source", "complete", generationPairs, "P1 · 生成の探索的比較 · 正解率の差と95% CI（percentage points）")}
<p>36 familyをクラスターとし、natural/nonceをfamily内で平均、深さ×fillerの各セルを等重みにした保存済み統計。セル内で10,000回のbootstrap、seed 20261005。複数の比較を見たことへの補正はしていない。C−EのCIは元のCSVに保存されていないため追加計算しない。${source("pilot", "paired-comparisons.csv", "元のpaired-comparisons.csv")}</p>
$p2Generation
<h3>部分群と実行分母を確認する。</h3>
<p class="muted">以下は保存済みCSVの重複しないセルの記述集計。割合は正解数 / 評価可能数で、選択した部分群のCIを新しく計算しない。元のreading mainを選ぶと読み取り課題だけが表示される。</p>
<form class="filters" aria-label="結果の絞り込み"><label>実験<select id="run">$runOptions</select></label><label>課題<select id="task"><option value="ast_to_source">ast_to_source · 全文生成</option></select></label><label>観測位置<select id="view"><option value="complete">complete</option></select></label><label>語彙<select id="lex"><option value="ALL">natural + nonce</option><option value="natural">natural</option><option value="nonce">nonce</option></select></label><label>深さ<select id="depth"><option value="ALL">全 depth</option></select></label><label>filler<select id="filler"><option value="ALL">全 filler</option></select></label></form>
<p id="selection-status" class="selection-status" role="status" aria-live="polite">P1 pilot · ast_to_source / complete / natural + nonce / 全 depth / 全 filler</p>
<div id="accuracy-chart" class="accuracy-chart" role="img" aria-label="P1全文生成の各表記の正解率"></div>
<div class="table-scroll"><table id="results-table"><caption>条件別の正解数と実行分母 · CIを伴わない記述集計</caption><thead><tr><th scope="col">表記</th><th scope="col">正解 / 評価可能</th><th scope="col">正解率</th><th scope="col">計画</th><th scope="col">送信</th><th scope="col">処置保存</th><th scope="col">infra missing</th><th scope="col">未送信</th></tr></thead><tbody>$initialRows</tbody></table></div>
<noscript><p>JavaScript無効時はP1生成の全条件を表示する。部分群は ${source("pilot", "summary.csv", "CSV")} を参照。</p></noscript>
</section>
<section id="interpretation">
<p class="section-label">05 / INTERPRETATION</p><h2>生成の利点は観測できた。機構の切り分けは残る。</h2>
<p>このP1結果は、こうした冗長な終端が未知の小言語の生成を助ける、という仮説に沿っている。C−Bは種類ラベルの利点、D−Cは名前を加える利点を示す探索的結果。D−Eは、少なくとも今回の固定suffixによる文字数の増加だけではDの成績を説明しきれないことを示す。</p>
<p>ただし「長距離依存を必要としなくなった」と直接実証したわけではない。閉じる種類や名前を提示する表記が、生成過程の検索、区切りの識別、注意、tokenization等を助けた可能性もある。名前を正しく再現する対応関係、ネストの深さ、閉じる順序を保つ必要は残る。正解率・reasoning tokens・遅延から内部の認知負荷やFLOPsを読み取らない。</p>
<p>測定対象は、明示された文法と固定8例の下での<strong>全文AST一致</strong>。構造に加えて識別子や値のコピーも含むため、純粋な閉じ括弧処理だけの指標ではない。有限のdepth/filler、固定語彙、単一モデルと設定に条件づけられ、任意の未知言語へ一般化した結論ではない。</p>
<p>P1生成差は探索的で、旧reading mainの事前固定主仮説とは別。P2も別の探索的pilotとして扱う。生成についてconfirmatoryな判断を得るには、生成を主目的にした新しい固定計画と独立holdoutが必要になる。既存の凍結済み計画を読み替えない。</p>
</section>
<section id="adjunct">
<p class="section-label">06 / ADJUNCT TASKS</p><h2>全文parsingと読み取りは、生成を補足する別の結果。</h2>
<h3>全文parsing：生成したい木を、読んで復元できるか。</h3>
<p>P2の <code>source_to_ast</code> は完全に閉じた全文から、中立の入れ子JSON ASTを復元する課題。生成方向と同じfamily・名前・値・例・語彙・5表記を使う。全nodeと順序を比較し、schemaが正しくても違う木なら失敗。生成の入力はparent/order表、parsingの出力は入れ子JSONなので、絶対正解率の差には表現の違いも含まれる。</p>
$p2Parsing
<h3>prefix読み取り：probe地点の値と開いているブロック。</h3>
<p><code>scope_lookup</code> は見える変数値を整数ひとつで、<code>active_stack</code> は開いているscope名を外側から内側のJSON配列で答える。入力はprobeで止め、後続の閉じラベルは見せない。EOFで開いたscopeが残るのは意図したprefixで、全文生成・全文parsingとは違う課題。</p>
$readingComparisons
<p>P1 T1のCIはゼロを含む。before-closeではB/Dが全正解で差0、退化した区間[0,0]だったが、一般的な同等性の証明ではない。読み取り結果を生成結果とプールしない。</p>
<h3>元のfrozen main：主目的を保持した読み取り実験。</h3>
<p>${h(mainNotice)}</p>
$mainComparison
<p>この5,120試行の計画上の主比較は <code>scope_lookup / after_close / D−B</code>。全体の処置保存数とCI状態を元のCSVから表示し、生成のconfirmatory試験には変更しない。結果が有利な時点で止めたり、失敗を除いて再送したりする計画ではない。${source("main", "report.md", "保存済みmain report")} / ${source("main", "paired-comparisons.csv", "保存済み比較CSV")}</p>
</section>
<section id="status">
<p class="section-label">07 / STATUS AND REPRODUCIBILITY</p><h2>測定、配線検証、計画を分けて記録する。</h2>
<div class="status-grid"><article><span class="pill complete">P1 / LIVE PILOT</span><h3>探索的生成＋補助読み取り</h3><p>${h(counts(pilot))}</p><p><code>synthetic_mock=false</code>。生成360応答とprefix読み取り1,440応答を区別する。${source("pilot", "report.md", "保存済みreport")}</p></article><article><span class="pill ${if mainComplete then "complete" else "partial"}">MAIN / ${if mainComplete then "TERMINAL COMPLETE" else "SNAPSHOT"}</span><h3>別のfrozen reading main</h3><p>${h(mainNotice)}</p><p>未送信の内訳、利用量、欠測感度は元のreportに保持。${source("main", "report.md", "保存済みreport")}</p></article><article><span class="pill ${if p2.exists(r => r.total.number("terminal") == r.total.number("planned")) then "complete" else if p2.nonEmpty || p2Execution.nonEmpty then "partial" else "pending"}">P2 / ${if p2.nonEmpty then "LIVE ARTIFACTS" else if p2Execution.nonEmpty then "LIVE STARTED · UNGRADED" else "NO SAVED LIVE STATUS"}</span><h3>別の全文生成・parsing pilot</h3><p>$p2StatusText</p>$p2ProgressAfterReport<p>$p2ImplementationText</p><p>${link("docs/full-program-structures.md", "別のP2設計")} / ${link("supporting/full-program-pilot.json", "固定config")}</p></article></div>
<p>P1の既知使用量は${comma(pilot.total.number("unique_total_tokens_known"))} tokens、mainは${comma(main.total.number("unique_total_tokens_known"))} tokens。${p2.map(r => s"P2 liveは${comma(r.total.number("unique_total_tokens_known"))} known tokens。").getOrElse("")} $reservation これはbillingの確定額や無料quotaの証拠ではない。</p>
${detail("P2 mockの配線検証の詳細", if p2MockVerified then s"<p><code>synthetic_mock=true</code>。960/960 terminal・strict correct、1,920 simulated adapter attempts。保存済み5レポートの再生成はbyte-identical、immutable artifactは不変、resumeの追加logical trial / adapter attemptはともに0。外部HTTPは0（明示的な--mockコード経路の確認で、packet captureではない）。<a href=\"${h(p2MockWorkflowRunUrl)}/attempts/3\">実際のmock workflow（attempt 3）</a> / ${link(p2MockWitnessPath, "保存済み検証証跡")}。モデルの成績・仮説の支持として数えない。</p>" else "<p>保存済みwitnessがないため、通しmock完了を主張しない。</p>")}
</section>
<section id="artifacts">
<p class="section-label">APPENDIX / ARTIFACTS</p><h2>数値から、保存資料へ戻れる。</h2>
<p>HTMLは保存済みScala採点・統計成果物だけから生成する。再採点、再bootstrap、API接続は行わない。部分群の割合はCSVの正解数と評価可能数の比で、CIは <code>paired-comparisons.csv</code> からコピーする。以下は実際にコピーした各ファイルのSHA-256。</p>
<div class="table-scroll"><table class="protocol-table"><caption>別々に保持するprotocolとsource</caption><thead><tr><th scope="col">実験</th><th scope="col">Protocol hash</th><th scope="col">Source hash</th><th scope="col">Download</th></tr></thead><tbody>$protocolRows</tbody></table></div>
<p>Pilot v1は完全なcanonical logical requestを保存したが、transport wireのJSON key orderをbyte-for-byteでは記録していない。再構成したbodyを当時のraw wireとは呼ばない。元のsource・採点・応答を維持し、変更後のmain/P2と統合しない。${link("docs/pilot-interpretation.md", "provenanceの説明")}</p>
${detail("公開資料のハッシュとダウンロード", s"<div class=\"table-scroll\"><table class=\"artifact-table\"><caption>Public input artifacts / SHA-256</caption><thead><tr><th scope=\"col\">File</th><th scope=\"col\">SHA-256</th></tr></thead><tbody>$artifacts</tbody></table></div>")}
<p class="muted">Input manifest SHA-256: <code class="hash">$fingerprint</code>。HTMLは外部asset・analytics・秘密鍵・ローカル絶対パスを含まない。</p>
<p><a href="build-provenance.json" download>公開buildの検証状態とworkflow識別子</a> / ${link("status.json", "保存資料の実行状態")}。再現用Scalaソースとデータはpublication projectにまとめている。</p>
</section>
</main>
<footer><div class="wrap"><p>Syntax locality ICL experiment · Scala 3.3.8 / JDK 21 / sbt 1.10.7</p><p>生成の探索的pilot / 元のfrozen reading main / 全文parsing / synthetic mockを別々に保持。</p></div></footer>
<script>const DATA = $dataJs;
const RUN_LABELS = $runLabels;
$browserScript
</script>
</body>
</html>
"""
  private def tableRow(style: String, correct: Long, evaluable: Long, planned: Long, dispatched: Long,
    terminal: Long, missing: Long, notDispatched: Long): String =
    s"<tr${if style == "named_end" then " class=\"named-row\"" else ""}><th scope=\"row\">${h(labels(style))}</th>" +
      s"<td>$correct / $evaluable</td><td>${percent(correct,evaluable)}</td><td>$planned</td><td>$dispatched</td>" +
      s"<td>$terminal</td><td>$missing</td><td>$notDispatched</td></tr>"

  private val css = """
.flow{display:flex;flex-wrap:wrap;align-items:center;gap:10px;padding:20px 24px;margin:24px 0;background:#edf3f9;border:1px solid #dce3eb;font-size:14px;font-weight:650}.flow span:nth-child(odd){padding:7px 10px;background:white;border:1px solid #dce3eb;border-radius:3px}.flow span:nth-child(even){color:#3877b9;font-size:20px}.syntax-card>p{min-height:3em;margin-bottom:0}.two-columns pre{margin-top:10px}@media(max-width:620px){.flow{padding:15px}.flow span:nth-child(even){font-size:16px}}
:root{--navy:#10243e;--ink:#182c45;--blue:#3877b9;--orange:#bd5a24;--paper:#fafbfc;--muted:#586a7d;--line:#dce3eb;--pale:#edf3f9}
*{box-sizing:border-box}html{scroll-behavior:smooth;scroll-padding-top:5rem}body{margin:0;background:var(--paper);color:var(--ink);font-family:system-ui,-apple-system,"Segoe UI","Yu Gothic",Meiryo,sans-serif;line-height:1.85;font-size:16px}a{color:#175b9a;text-underline-offset:3px}a:hover{color:var(--orange)}a:focus-visible,select:focus-visible,summary:focus-visible{outline:3px solid #e19955;outline-offset:4px}.wrap{max-width:1150px;margin:0 auto;padding:0 30px}.skip{position:absolute;left:10px;top:-100px;z-index:10;background:white;padding:12px}.skip:focus{top:10px}.hero{background:var(--navy);color:#f6f9fc;padding:64px 0 36px}.eyebrow,.section-label{font-size:12px;letter-spacing:.16em;font-weight:700}.eyebrow{color:#9dc5ed;margin:0 0 16px}.hero h1{font-size:clamp(30px,4.5vw,53px);line-height:1.5;letter-spacing:-.02em;margin:0 0 20px;font-weight:750}.lead{max-width:800px;color:#d2e0ee;font-size:18px}.hero-facts{display:grid;grid-template-columns:1fr 1fr 1fr;gap:28px;margin:36px 0 24px}.hero-facts>div{border-top:1px solid #49617c;padding-top:20px;display:flex;flex-direction:column}.hero-facts .big{font-size:38px;line-height:1.2;letter-spacing:-.03em;font-variant-numeric:tabular-nums;margin-bottom:12px}.hero-facts>div:first-child .big{color:#f4b078}.hero-facts>div>span:last-child{font-size:14px;color:#d2e0ee}.hero-source{font-size:12px;color:#b5c8dc}.hero a{color:#b9d9fa}nav{background:#fff;border-bottom:1px solid var(--line);position:sticky;top:0;z-index:2}nav .wrap{display:flex;gap:26px;overflow-x:auto;white-space:nowrap;padding-top:13px;padding-bottom:13px}nav a{font-size:13px;font-weight:700;text-decoration:none}main section{padding:48px 0;border-bottom:1px solid var(--line);scroll-margin-top:60px}.section-label{color:var(--blue);margin:0 0 8px}h2{font-size:clamp(23px,3vw,30px);line-height:1.6;margin:0 0 20px;letter-spacing:-.01em}h3{font-size:18px;line-height:1.6;margin:26px 0 12px}p{margin:0 0 18px}code{font-family:ui-monospace,SFMono-Regular,Consolas,"Liberation Mono",monospace;font-size:.88em;background:#edf1f5;border-radius:3px;padding:.1em .3em;overflow-wrap:anywhere}.hero code{background:#203b59}.muted{color:var(--muted);font-size:14px}.tasks{display:grid;grid-template-columns:1fr 1fr;gap:12px;margin:24px 0}.task{border-left:3px solid #88a8c9;background:var(--pale);padding:18px 20px}.task p{margin:10px 0 0;font-size:14px}.callout{background:#fff4ea;border-left:3px solid var(--orange);padding:20px 23px;margin:24px 0;font-size:14px}.callout strong{color:#924318}.two-columns{display:grid;grid-template-columns:1.15fr 1fr;gap:32px;margin:24px 0}.two-columns>div>h3:first-child{margin-top:0}.table-scroll{overflow-x:auto;border:1px solid var(--line);border-radius:3px;margin:18px 0 24px;background:#fff}table{border-collapse:collapse;width:100%;font-size:13px;font-variant-numeric:tabular-nums;text-align:left}caption{text-align:left;font-weight:650;color:var(--ink);padding:14px 16px;border-bottom:1px solid var(--line);background:#f3f6f9}th,td{padding:12px 14px;border-bottom:1px solid #e6ebf0;vertical-align:top;white-space:nowrap}thead th{font-size:12px;letter-spacing:.02em;color:#51647a;background:#fafbfc}tbody tr:last-child>*{border-bottom:0}tbody th{font-weight:600}.named-row,.focus-cell{background:#fff4e9}.filters{display:grid;grid-template-columns:repeat(6,minmax(100px,1fr));gap:14px;margin-top:20px}.filters label{font-size:12px;color:var(--muted);font-weight:650;display:flex;flex-direction:column;gap:7px}select{background:white;border:1px solid #b9c7d6;border-radius:3px;color:var(--ink);padding:10px 8px;font:inherit;min-width:0;font-size:12px}.selection-status{font-size:13px;margin:18px 0 14px;color:var(--muted)}.accuracy-chart{background:white;border:1px solid var(--line);padding:22px 24px;border-radius:3px}.bar-row{display:grid;grid-template-columns:135px minmax(60px,1fr) 140px;align-items:center;gap:14px;margin:12px 0;font-size:13px}.bar-track{background:#e8eef5;height:16px;position:relative;border-radius:2px}.bar-fill{background:var(--blue);height:100%;border-radius:2px}.bar-fill.named{background:var(--orange)}.bar-value{text-align:right;font-variant-numeric:tabular-nums}.bar-unmeasured{color:var(--muted);font-size:12px}.syntax-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:15px;margin:24px 0}.syntax-card{background:white;border:1px solid var(--line);padding:0 18px}.syntax-card h3{font-size:14px;margin:16px 0}.syntax-card:nth-child(4){border-top:3px solid var(--orange)}pre{font-size:12px;line-height:1.8;overflow:auto;padding:16px;background:#edf2f7}pre code{background:none;padding:0;overflow-wrap:normal}details{border:1px solid var(--line);border-radius:3px;background:white;margin:20px 0}summary{padding:16px 20px;font-size:14px;cursor:pointer;font-weight:650}details>p,details>pre{margin:0 20px 20px}details .table-scroll{margin:0;border-left:0;border-right:0}.status-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:18px;margin:24px 0}.status-grid article{background:white;border:1px solid var(--line);padding:22px}.status-grid p{font-size:14px}.pill{display:inline-block;font-size:10px;letter-spacing:.07em;font-weight:750;border-radius:20px;padding:4px 10px;line-height:1.6}.complete{background:#e9f2f9;color:#215886}.partial{background:#fff0df;color:#9a4b17}.pending{background:#eef0f3;color:#5d6775}.status-grid h3{margin:14px 0}.protocol-table td,.protocol-table th{white-space:normal}.protocol-table code{font-size:10px;background:transparent;word-break:break-all}.hash{font-size:10px;word-break:break-all}.artifact-table td:first-child{white-space:normal}.artifact-table code{background:transparent}footer{background:var(--navy);color:#b4c7dc;padding:30px 0;font-size:12px}footer p{margin:6px 0}@media(max-width:900px){.filters{grid-template-columns:repeat(3,1fr)}.status-grid{grid-template-columns:1fr}.two-columns{grid-template-columns:1fr;gap:12px}.hero-facts{gap:18px}.hero-facts .big{font-size:30px}}@media(max-width:620px){.wrap{padding-left:20px;padding-right:20px}.hero{padding-top:38px}.lead{font-size:16px}.hero-facts{grid-template-columns:1fr;gap:12px}.hero-facts>div{display:grid;grid-template-columns:135px 1fr;gap:12px;align-items:center;padding-top:15px}.hero-facts .big{font-size:26px;margin:0}.tasks,.syntax-grid{grid-template-columns:1fr}.filters{grid-template-columns:repeat(2,1fr)}.bar-row{grid-template-columns:105px 1fr;gap:8px}.bar-value{grid-column:2;text-align:left;font-size:11px;margin-top:-7px}.accuracy-chart{padding:14px}main section{padding-top:35px;padding-bottom:35px}nav .wrap{gap:20px}th,td{padding:10px}}@media(prefers-reduced-motion:reduce){html{scroll-behavior:auto}}@media print{nav,.filters{display:none}.hero{background:white;color:var(--ink);padding:10px 0}.hero p,.hero-facts>div>span:last-child{color:var(--muted)}.hero a{color:var(--blue)}.hero-facts .big{color:var(--ink)!important}.wrap{max-width:none;padding:0}.table-scroll{overflow:visible}main section{break-inside:avoid}footer{background:white;color:var(--muted)}}
"""

  private val browserScript = """
(() => {
  'use strict';
  const styles = ['braces','generic_end','typed_end','named_end','padded_end'];
  const labels = {'braces':'A · braces','generic_end':'B · generic end','typed_end':'C · typed end','named_end':'D · named end','padded_end':'E · padded end'};
  const fields = ['correct','model_evaluable','planned','dispatched','terminal','infrastructure_missing','not_dispatched'];
  const controls = Object.fromEntries(['run','task','view','lex','depth','filler'].map(id => [id,document.getElementById(id)]));
  const tbody = document.querySelector('#results-table tbody');
  const chart = document.getElementById('accuracy-chart');
  function options(control, values, allLabel) {
    const old = control.value;
    control.replaceChildren();
    if (allLabel) { const option=document.createElement('option'); option.value='ALL'; option.textContent=allLabel; control.append(option); }
    for (const value of values) { const option=document.createElement('option'); option.value=value; option.textContent=value; control.append(option); }
    if ([...control.options].some(option => option.value===old)) control.value=old;
  }
  function synchronize() {
    const rows=DATA[controls.run.value];
    options(controls.task,[...new Set(rows.map(row=>row.task))].sort(),null);
    const taskRows=rows.filter(row=>row.task===controls.task.value);
    options(controls.view,[...new Set(taskRows.map(row=>row.view))].sort(),null);
    const viewRows=taskRows.filter(row=>row.view===controls.view.value);
    options(controls.depth,[...new Set(viewRows.map(row=>row.depth))].sort((a,b)=>Number(a)-Number(b)),'全 depth');
    options(controls.filler,[...new Set(viewRows.map(row=>row.filler))].sort((a,b)=>Number(a)-Number(b)),'全 filler');
  }
  function render() {
    const choice=Object.fromEntries(Object.entries(controls).map(([id,control])=>[id,control.value]));
    const selected=DATA[choice.run].filter(row=>row.task===choice.task && row.view===choice.view &&
      (choice.lex==='ALL'||row.lexical_regime===choice.lex) && (choice.depth==='ALL'||row.depth===choice.depth) &&
      (choice.filler==='ALL'||row.filler===choice.filler));
    const runName=RUN_LABELS[choice.run];
    document.getElementById('selection-status').textContent=[runName,choice.task,choice.view,choice.lex==='ALL'?'natural + nonce':choice.lex,choice.depth==='ALL'?'全 depth':'depth '+choice.depth,choice.filler==='ALL'?'全 filler':'filler '+choice.filler].join(' / ');
    tbody.replaceChildren(); chart.replaceChildren();
    let accessible=[];
    for (const style of styles) {
      const totals=Object.fromEntries(fields.map(key=>[key,0]));
      for (const row of selected.filter(row=>row.style===style)) for (const key of fields) totals[key]+=Number(row[key]);
      const accuracy=totals.model_evaluable===0?null:100*totals.correct/totals.model_evaluable;
      const percentage=accuracy===null?'NA':accuracy.toFixed(3)+'%';
      const tr=document.createElement('tr'); if(style==='named_end') tr.className='named-row';
      const values=[labels[style],totals.correct+' / '+totals.model_evaluable,percentage,totals.planned,totals.dispatched,totals.terminal,totals.infrastructure_missing,totals.not_dispatched];
      values.forEach((value,index)=>{const cell=document.createElement(index===0?'th':'td');if(index===0)cell.scope='row';cell.textContent=value;tr.append(cell);});
      tbody.append(tr);
      const row=document.createElement('div');row.className='bar-row';
      const label=document.createElement('span');label.textContent=labels[style];
      const track=document.createElement('div');track.className='bar-track';
      if(accuracy!==null){const fill=document.createElement('div');fill.className='bar-fill'+(style==='named_end'?' named':'');fill.style.width=accuracy+'%';track.append(fill);}
      else{const empty=document.createElement('span');empty.className='bar-unmeasured';empty.textContent='評価可能な応答なし';track.append(empty);}
      const value=document.createElement('span');value.className='bar-value';value.textContent=percentage+' · '+totals.correct+'/'+totals.model_evaluable;
      row.append(label,track,value);chart.append(row);accessible.push(labels[style]+': '+value.textContent);
    }
    chart.setAttribute('aria-label',accessible.join('。'));
  }
  for (const control of Object.values(controls)) control.addEventListener('change',()=>{synchronize();render();});
  synchronize();render();
})();
"""
