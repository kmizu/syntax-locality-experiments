package locality.bench.report

import java.nio.file.{Path, Files}
import java.nio.charset.StandardCharsets
import java.util.Locale
import locality.bench.stats.*
import locality.bench.score.*
import locality.llm.json.*

final case class ReportMetadata(
    preset: String,
    phase: String,
    protocolHash: String,
    model: String,
    startedAt: String,
    syntheticMock: Boolean,
    bootstrapSeed: Long = 20261005L,
    lexicalRegimes: Vector[String] = Vector("natural", "nonce"),
    conditions: String = "",
    protocolVersion: String = "1"
)

final case class Denominators(
    planned: Int,
    dispatched: Int,
    terminal: Int,
    evaluable: Int,
    correct: Int,
    infrastructureMissing: Int,
    notDispatched: Int,
    incomplete: Int,
    refusal: Int,
    operationalLower: Option[Double],
    operationalUpper: Option[Double]
)

object Report:
  def denominators(observations: Vector[TrialObservation]): Denominators =
    val dispatched = observations.count(_.dispatched)
    val correct = observations.count(r => r.score.evaluable && r.score.strictCorrect)
    val unresolved = observations.count(r => r.dispatched && (!r.terminal || r.score.outcome == Outcome.AmbiguousOutcome))
    Denominators(observations.size, dispatched, observations.count(_.terminal), observations.count(_.score.evaluable),
      correct, observations.count(_.score.infrastructureMissing), observations.count(_.score.outcome == Outcome.NotDispatched),
      observations.count(_.score.outcome == Outcome.IncompleteOutput), observations.count(_.score.outcome == Outcome.Refusal),
      if dispatched == 0 then None else Some(correct.toDouble / dispatched),
      if dispatched == 0 then None else Some((correct + unresolved).toDouble / dispatched))

  /** Pure saved-data analysis: this object has no LLM client, transport, or credentials. */
  def write(runDir: Path, metadata: ReportMetadata, observations: Vector[TrialObservation]): Unit =
    require(observations.map(_.trialId).distinct.size == observations.size, "Duplicate trials in report input")
    Files.createDirectories(runDir)
    val rows = observations.sortBy(_.trialId)
    val fullProgramSuite = metadata.phase == "p2"
    val d = denominators(rows)
    val complete = rows.nonEmpty && rows.forall(r => r.dispatched && r.terminal && r.score.outcome != Outcome.AmbiguousOutcome)
    val primaryRows = rows.filter(r => r.task == "scope_lookup" && r.view == "after_close")
    val primary = PairedAnalysis.compare(primaryRows, lexicalRegimes = metadata.lexicalRegimes)
    val comparisons = rows.groupBy(r => (r.task, r.view)).toVector.sortBy(_._1).flatMap { case ((task, view), rs) =>
      Vector(("named_end", "generic_end"), ("named_end", "padded_end"), ("named_end", "typed_end"),
        ("typed_end", "generic_end"), ("named_end", "braces")).map { (t, b) =>
        val result = PairedAnalysis.compare(rs, t, b, metadata.lexicalRegimes)
        val bootstrap = if complete && metadata.preset != "smoke" && result.completeStrata && result.availableFamilies > 1 then
          Some(ClusterBootstrap.run(result.familyDifferences, 10000, metadata.bootstrapSeed))
        else None
        (task, view, result, bootstrap)
      }
    }
    val summaries = rows.groupBy(r => (r.task, r.view, r.style)).toVector.sortBy(_._1)
    val details = rows.groupBy(r => (r.task, r.view, r.style, r.lexicalRegime, r.depth, r.filler)).toVector.sortBy(_._1)
    val summaryHeader = Vector("task", "view", "style", "lexical_regime", "depth", "filler") ++ metricsHeader
    val pooledDetails = rows.groupBy(r => (r.lexicalRegime, r.depth, r.filler)).toVector.sortBy(_._1)
    val summaryRows = Vector(Vector.fill(6)("ALL") ++ metrics(rows)) ++
      summaries.map { case ((task, view, style), rs) => Vector(task, view, style, "ALL", "ALL", "ALL") ++ metrics(rs) } ++
      details.map { case ((task, view, style, lexical, depth, filler), rs) => Vector(task, view, style, lexical, depth.toString, filler.toString) ++ metrics(rs) } ++
      pooledDetails.map { case ((lexical, depth, filler), rs) => Vector("ALL", "ALL", "ALL", lexical, depth.toString, filler.toString) ++ metrics(rs) }
    writeCsv(runDir.resolve("summary.csv"), summaryHeader, summaryRows)
    val pairedHeader = Vector("task", "view", "treatment", "baseline", "classification", "matched_families_available", "matched_families_planned",
      "difference_percentage_points", "baseline_accuracy", "treatment_accuracy", "relative_error_reduction", "ci_status", "ci_lower_pp", "ci_upper_pp",
      "bootstrap_seed", "bootstrap_iterations", "degenerate", "discordant_families", "complete_strata", "missing_worst_case_pp", "missing_best_case_pp", "mcnemar_exact_descriptive_p")
    val pairedRows = comparisons.map { (task, view, result, bootstrap) =>
      Vector(task, view, result.treatment, result.baseline,
        if metadata.preset == "main" && task == "scope_lookup" && view == "after_close" && result.treatment == "named_end" && result.baseline == "generic_end" then "primary" else "exploratory",
        result.availableFamilies.toString, result.plannedFamilies.toString, percentagePoints(result.difference), number(result.baselineAccuracy), number(result.treatmentAccuracy),
        number(result.relativeErrorReduction), ciStatus(bootstrap), percentagePoints(bootstrap.map(_.lower)), percentagePoints(bootstrap.map(_.upper)),
        metadata.bootstrapSeed.toString, bootstrap.fold("0")(_.iterations.toString), bootstrap.exists(_.degenerate).toString,
        result.discordantFamilies.toString, result.completeStrata.toString, percentagePoints(result.missingWorstCase), percentagePoints(result.missingBestCase), number(result.mcnemar))
    }
    writeCsv(runDir.resolve("paired-comparisons.csv"), pairedHeader, pairedRows)
    val astDiagnosticKeys = if fullProgramSuite then Vector("ast_error_code", "ast_error_path", "ast_mismatch_class", "ast_mismatch_path") else Vector.empty
    val errorRows = rows.filter(r => !r.score.strictCorrect).map { r =>
      Vector(r.trialId, r.familyId, r.task, r.view, r.style, r.lexicalRegime, r.score.outcome.id, r.score.reason,
        r.score.diagnostics.getOrElse("parse_error_code", ""), r.score.diagnostics.getOrElse("parse_error_line", ""),
        r.score.diagnostics.getOrElse("parse_error_column", ""), r.score.evaluable.toString, r.dispatched.toString, r.terminal.toString,
        r.score.diagnostics.getOrElse("element_count_difference", ""), r.score.diagnostics.getOrElse("unknown_names", ""),
        r.score.diagnostics.getOrElse("closed_names", ""), r.score.diagnostics.getOrElse("order_mismatch", "")) ++ astDiagnosticKeys.map(r.score.diagnostics.getOrElse(_, ""))
    }
    writeCsv(runDir.resolve("errors.csv"), Vector("trial_id", "family_id", "task", "view", "style", "lexical_regime", "outcome", "reason",
      "first_parse_error_code", "first_parse_error_line", "first_parse_error_column", "model_evaluable", "dispatched", "terminal",
      "element_count_difference", "unknown_names", "closed_names", "order_mismatch") ++ astDiagnosticKeys, errorRows)
    val scores = rows.map { r =>
      Json.canonical(Json.obj("trial_id" -> Str(r.trialId), "family_id" -> Str(r.familyId), "outcome" -> Str(r.score.outcome.id),
        "strict_correct" -> Bool(r.score.strictCorrect), "model_evaluable" -> Bool(r.score.evaluable),
        "infrastructure_missing" -> Bool(r.score.infrastructureMissing), "auxiliary_correct" -> r.score.auxiliaryCorrect.fold[JsonValue](Null)(Bool.apply),
        "syntax_valid" -> r.score.syntaxValid.fold[JsonValue](Null)(Bool.apply), "reason" -> Str(r.score.reason),
        "diagnostics" -> Obj(r.score.diagnostics.toVector.sortBy(_._1).map((k, v) => k -> Str(v)))))
    }.mkString("", "\n", if rows.isEmpty then "" else "\n")
    writeText(runDir.resolve("scores.jsonl"), scores)
    val sb = new StringBuilder
    sb.append("# Syntax locality ICL experiment report\n\n")
    sb.append("## 1. Run status and summary\n\n")
    sb.append(s"Status: **${if complete then "all planned trials terminal" else "provisional / incomplete run"}**. synthetic_mock=${metadata.syntheticMock}.\n\n")
    if metadata.syntheticMock then sb.append("These synthetic fixtures check experiment wiring. They are not model performance measurements or evidence for a syntax advantage.\n\n")
    if metadata.preset == "smoke" then sb.append("Smoke is an operational check; confidence intervals are not_computed.\n\n")
    if fullProgramSuite then sb.append(s"Strict model-evaluable accuracy: ${percentage(ratio(d.correct, d.evaluable))}. Both full-program directions are exploratory.\n\n")
    else sb.append(s"Strict model-evaluable accuracy: ${percentage(ratio(d.correct, d.evaluable))}; observed primary D−B: ${percentagePoints(primary.difference)} percentage points.\n\n")
    sb.append("## 2. Experiment conditions\n\n")
    sb.append(s"Preset: ${metadata.preset}; phase: ${metadata.phase}; model: `${metadata.model}`; started: ${metadata.startedAt}; protocol version: ${metadata.protocolVersion}; protocol hash: `${metadata.protocolHash}`.\n\n")
    sb.append(s"Bootstrap seed: ${metadata.bootstrapSeed}; iterations when eligible: 10,000; unit: structural family; strata: depth × filler; lexical regimes: ${metadata.lexicalRegimes.mkString(", ")}.\n\n")
    if metadata.conditions.nonEmpty then sb.append(fenced(metadata.conditions)).append("\n")
    sb.append("## 3. Planned / executed counts and missingness\n\n")
    sb.append(table(Vector("planned", "dispatched", "terminal", "model-evaluable", "correct", "infrastructure missing", "not-dispatched", "incomplete", "refusal"),
      Vector(Vector(d.planned, d.dispatched, d.terminal, d.evaluable, d.correct, d.infrastructureMissing, d.notDispatched, d.incomplete, d.refusal).map(_.toString))))
    if !fullProgramSuite then sb.append(s"\nPrimary matched families: ${primary.availableFamilies} / ${primary.plannedFamilies}. ")
    else sb.append("\n")
    sb.append(s"Operational success correct/dispatched: ${percentage(d.operationalLower)}; upper bound if unresolved dispatched outcomes all succeed: ${percentage(d.operationalUpper)}.\n\n")
    if metadata.preset == "main" && ratio(d.infrastructureMissing, d.planned).exists(_ > 0.01) then
      sb.append("Infrastructure missingness exceeds 1% of planned trials. Investigate causes and imbalance across conditions before drawing a definitive superiority conclusion.\n\n")
    sb.append("Missing trials are not model errors. Comparison sensitivity bounds in paired-comparisons.csv assign all non-evaluable planned outcomes against / in favor of D.\n\n")
    sb.append(if fullProgramSuite then "## 4. Exploratory parsing and generation D−B comparisons\n\n" else "## 4. Primary paired D−B comparison\n\n")
    val primaryComparison = comparisons.filter { x =>
      x._3.treatment == "named_end" && x._3.baseline == "generic_end" &&
        (if fullProgramSuite then Set("ast_to_source", "source_to_ast")(x._1) && x._2 == "complete" else x._1 == "scope_lookup" && x._2 == "after_close")
    }
    sb.append(comparisonTable(primaryComparison))
    if fullProgramSuite then sb.append("\nNo confirmatory reading-primary comparison is planned in P2. Parsing and generation remain separate outcomes.\n\n")
    else sb.append(s"\nRelative error reduction: ${number(primary.relativeErrorReduction)} (NA at zero baseline error). Missingness sensitivity: [${percentagePoints(primary.missingWorstCase)}, ${percentagePoints(primary.missingBestCase)}] percentage points.\n\n")
    sb.append("Replicates are averaged within family and lexical regime, regimes have equal weight, and depth × filler cells have equal weight. A wholly missing planned cell leaves the overall effect undefined. Exact McNemar values, where available for single-regime unaveraged binary family pairs, are descriptive.\n\n")
    if !fullProgramSuite && primaryComparison.exists(_._4.exists(_.degenerate)) then
      sb.append(s"Degenerate bootstrap distribution: observed discordant families=${primary.discordantFamilies}, baseline=${percentage(primary.baselineAccuracy)}, D=${percentage(primary.treatmentAccuracy)}. The interval does not establish an exactly zero population effect; inspect ceiling/floor effects.\n\n")
    sb.append("## 5. Five syntaxes: accuracy, truncation, and usage\n\n")
    sb.append(table(Vector("task", "view", "style", "evaluable", "accuracy", "incomplete", "input known", "output known", "reasoning known", "cached known", "tokens/correct", "retry rate", "mean latency ms"),
      summaries.map { case ((task, view, style), rs) =>
        val ds = denominators(rs)
        Vector(task, view, style, ds.evaluable.toString, percentage(ratio(ds.correct, ds.evaluable)), ds.incomplete.toString,
          tokenSum(rs, _.inputTokens).toString, tokenSum(rs, _.outputTokens).toString, tokenSum(rs, _.reasoningTokens).toString, tokenSum(rs, _.cachedTokens).toString,
          tokensPerCorrect(rs), percentage(ratio(rs.count(_.generationAttempts > 1), ds.dispatched)), meanLatency(rs))
      }))
    val attemptKnown = rows.flatMap(_.attemptUsage).flatMap(_.totalTokens).sum
    val uniqueKnown = rows.flatMap(_.usage).flatMap(_.totalTokens).sum
    sb.append(s"\nKnown tokens from unique final trial usage: $uniqueKnown; known generation-attempt tokens across retries: $attemptKnown; trials without full input/output usage: ${rows.count(!_.usage.exists(_.totalTokens.isDefined))}; generation attempts without full usage: ${rows.map(_.generationAttempts).sum - rows.flatMap(_.attemptUsage).count(_.totalTokens.isDefined)}.\n\n")
    sb.append(s"HTTP attempts (count, generation, retry): ${rows.map(_.httpAttempts).sum}; known estimated cost USD: ${number(if rows.flatMap(_.estimatedCostUsd).isEmpty then None else Some(rows.flatMap(_.estimatedCostUsd).sum))}; cost-known trials: ${rows.count(_.estimatedCostUsd.isDefined)} / ${rows.size}. Estimates are not verified billing or free-quota balances.\n\n")
    sb.append("Unknown usage is excluded from known sums and explicitly counted, never replaced with a zero estimate. Cost ratios are NA when usage is incomplete or there are no correct answers. summary.csv separately reports unique-trial and all-generation-attempt tokens per correct result. Non-reasoning output is output minus reasoning only where both fields exist; it is not an exact answer-text token count. Retried and non-retried latency summaries are in summary.csv.\n\n")
    sb.append("## 6. Lexical regime, depth, and filler breakdown\n\n")
    sb.append("Full task/view/style/natural-or-nonce/depth/filler counts, usage completeness, and outcomes are in [summary.csv](summary.csv). Each row preserves its original trial denominator.\n\n")
    if fullProgramSuite then
      val breakdown = rows.groupBy(r => (r.task, r.view, r.lexicalRegime, r.depth, r.filler)).toVector.sortBy(_._1)
      sb.append(table(Vector("task", "view", "lexical", "depth", "filler", "planned", "evaluable", "accuracy", "infra missing"), breakdown.map { case ((task, view, lex, dep, fill), rs) =>
        val ds = denominators(rs)
        Vector(task, view, lex, dep.toString, fill.toString, ds.planned.toString, ds.evaluable.toString, percentage(ratio(ds.correct, ds.evaluable)), ds.infrastructureMissing.toString)
      }))
    else
      val breakdown = rows.groupBy(r => (r.lexicalRegime, r.depth, r.filler)).toVector.sortBy(_._1)
      sb.append(table(Vector("lexical", "depth", "filler", "planned", "evaluable", "accuracy", "infra missing"), breakdown.map { case ((lex, dep, fill), rs) =>
        val ds = denominators(rs)
        Vector(lex, dep.toString, fill.toString, ds.planned.toString, ds.evaluable.toString, percentage(ratio(ds.correct, ds.evaluable)), ds.infrastructureMissing.toString)
      }))
    sb.append("\n## 7. Exploratory paired comparisons\n\n")
    sb.append(comparisonTable(comparisons.filterNot(primaryComparison.contains)))
    sb.append("\nAll D−E, D−C, C−B, A contrasts, other tasks/views, and pilot comparisons are exploratory unless a separate testing plan was frozen. See [paired-comparisons.csv](paired-comparisons.csv).\n\n")
    sb.append(if fullProgramSuite then "## 8. Full-program parsing and generation\n\n" else "## 8. Reading and generation\n\n")
    val tasks = rows.map(_.task).distinct.sorted
    sb.append(s"Tasks actually planned in this saved run: ${tasks.mkString(", ")}.\n\n")
    if fullProgramSuite then
      sb.append("Source-to-AST is manual parsing: the model receives complete rendered source and returns nested AST JSON without using a parser tool. AST-to-source receives a shuffled AST table and returns complete program source. The directions share structural families and the example bank, but their input/output representations are not symmetric. Exact AST equality is scored separately for each direction.\n\n")
      sb.append("For source_to_ast, syntax_valid and strict_syntax_validity describe the returned JSON AST schema, not the source language's syntax. For ast_to_source they describe the generated source grammar. Schema-valid but incorrect ASTs fail strict correctness. AST codec and mismatch diagnostics are retained in scores.jsonl, errors.csv, and the failure examples.\n\n")
    else if tasks.contains("ast_to_source") then
      sb.append("Generation is assessed independently by strict syntax and exact AST equality. Its shorter capped AST bodies and longer correct D/E outputs differ from long-prefix reading tasks.\n\n")
    sb.append("Compare task-specific effects and output tokens in the tables; parsing and generation improvements are separate observations.\n\n")
    sb.append("## 9. Representative failures\n\n")
    if fullProgramSuite then
      tasks.foreach(task => sb.append(s"### $task\n\n").append(failureExamples(runDir, rows.filter(_.task == task))))
    else sb.append(failureExamples(runDir, rows))
    sb.append("\n## 10. Limitations and next experiments\n\n")
    sb.append("The benchmark tests explained miniature grammars plus fixed few-shot examples, not grammar learning from examples alone. The eight fixed nonce vocabularies do not represent every artificial vocabulary. Indentation is non-semantic; D/E character-length matching is not token matching.\n\n")
    sb.append("Accuracy, token usage, reasoning usage, caching, and latency are observable task and service outcomes; they do not directly measure cognitive load, dependency distance inside a model, or FLOPs. First parse errors are not counts of every syntax error. Self-contained fences are auxiliary normalization only.\n\n")
    sb.append("An observed zero or undetected difference does not prove general equivalence. Differences can reflect retrieval cues, delimiter identification, tokenization, or prompt length. Missingness, ceilings/floors, generation output burden, and method-specific measurement limits constrain causal explanations.\n\n")
    sb.append("Follow-up candidates after a new preregistered holdout: indentation, same-kind nesting, number of examples, reasoning effort, repair tasks, local-window cuts, and input-token matching. Do not remove failures or stop when an interval first becomes favorable.\n")
    writeText(runDir.resolve("report.md"), sb.toString)

  private val metricsHeader = Vector("planned", "dispatched", "terminal", "model_evaluable", "correct", "strict_accuracy", "infrastructure_missing", "not_dispatched", "incomplete", "refusal",
    "operational_lower", "operational_upper", "input_tokens_known", "input_usage_known_trials", "output_tokens_known", "output_usage_known_trials",
    "reasoning_tokens_known", "reasoning_usage_known_trials", "cached_tokens_known", "cached_usage_known_trials", "non_reasoning_output_known", "non_reasoning_known_trials",
    "unique_total_tokens_known", "unique_full_usage_trials", "attempt_total_tokens_known", "attempt_full_usage_count", "http_attempts", "generation_attempts",
    "retry_rate", "mean_latency_ms", "mean_latency_no_retry_ms", "mean_latency_retry_ms", "tokens_per_correct", "attempt_tokens_per_correct", "estimated_cost_usd_known", "cost_known_trials",
    "invalid_answer_format", "wrong_answer", "strict_syntax_validity", "syntax_evaluable_trials", "invalid_generated_syntax", "valid_syntax_wrong_ast")

  private def metrics(rs: Vector[TrialObservation]): Vector[String] =
    val d = denominators(rs)
    val usages = rs.flatMap(_.usage)
    val attemptUsages = rs.flatMap(_.attemptUsage)
    val syntax = rs.flatMap(_.score.syntaxValid)
    Vector(d.planned.toString, d.dispatched.toString, d.terminal.toString, d.evaluable.toString, d.correct.toString, number(ratio(d.correct, d.evaluable)),
      d.infrastructureMissing.toString, d.notDispatched.toString, d.incomplete.toString, d.refusal.toString, number(d.operationalLower), number(d.operationalUpper),
      tokenSum(rs, _.inputTokens).toString, tokenCount(rs, _.inputTokens).toString, tokenSum(rs, _.outputTokens).toString, tokenCount(rs, _.outputTokens).toString,
      tokenSum(rs, _.reasoningTokens).toString, tokenCount(rs, _.reasoningTokens).toString, tokenSum(rs, _.cachedTokens).toString, tokenCount(rs, _.cachedTokens).toString,
      usages.flatMap(_.nonReasoningOutput).sum.toString, usages.count(_.nonReasoningOutput.isDefined).toString,
      usages.flatMap(_.totalTokens).sum.toString, usages.count(_.totalTokens.isDefined).toString, attemptUsages.flatMap(_.totalTokens).sum.toString,
      attemptUsages.count(_.totalTokens.isDefined).toString, rs.map(_.httpAttempts).sum.toString, rs.map(_.generationAttempts).sum.toString,
      number(ratio(rs.count(_.generationAttempts > 1), d.dispatched)), meanLatency(rs), meanLatency(rs.filter(_.generationAttempts <= 1)), meanLatency(rs.filter(_.generationAttempts > 1)),
      tokensPerCorrect(rs), attemptTokensPerCorrect(rs), number(if rs.flatMap(_.estimatedCostUsd).isEmpty then None else Some(rs.flatMap(_.estimatedCostUsd).sum)), rs.count(_.estimatedCostUsd.isDefined).toString,
      rs.count(_.score.outcome == Outcome.InvalidAnswerFormat).toString, rs.count(_.score.outcome == Outcome.WrongAnswer).toString,
      number(ratio(syntax.count(identity), syntax.size)), syntax.size.toString, rs.count(_.score.outcome == Outcome.InvalidGeneratedSyntax).toString, rs.count(_.score.outcome == Outcome.ValidSyntaxWrongAst).toString)

  private def comparisonTable(cs: Vector[(String, String, PairedResult, Option[BootstrapResult])]): String =
    table(Vector("task", "view", "contrast", "families", "effect pp", "95% CI pp", "discordant", "CI status"), cs.map { (task, view, r, ci) =>
      Vector(task, view, s"${r.treatment} − ${r.baseline}", s"${r.availableFamilies}/${r.plannedFamilies}", percentagePoints(r.difference),
        ci.fold("not_computed")(b => s"[${number(Some(b.lower * 100))}, ${number(Some(b.upper * 100))}]"), r.discordantFamilies.toString, ciStatus(ci))
    })

  private def ciStatus(ci: Option[BootstrapResult]): String = ci.fold("not_computed")(b => if b.degenerate then "degenerate" else "computed")
  private def ratio(n: Int, d: Int): Option[Double] = if d == 0 then None else Some(n.toDouble / d)
  private def number(n: Option[Double]): String = n.fold("NA")(v => String.format(Locale.ROOT, "%.3f", Double.box(v)))
  private def percentage(n: Option[Double]): String = n.fold("NA")(v => number(Some(v * 100)) + "%")
  private def percentagePoints(n: Option[Double]): String = number(n.map(_ * 100))
  private def tokenSum(rs: Vector[TrialObservation], f: TokenUsage => Option[Long]): Long = rs.flatMap(_.usage).flatMap(f).sum
  private def tokenCount(rs: Vector[TrialObservation], f: TokenUsage => Option[Long]): Int = rs.flatMap(_.usage).count(u => f(u).isDefined)
  private def meanLatency(rs: Vector[TrialObservation]): String =
    val values = rs.flatMap(_.latencyMs)
    number(if values.isEmpty then None else Some(values.map(_.toDouble).sum / values.size))
  private def tokensPerCorrect(rs: Vector[TrialObservation]): String =
    val correct = rs.count(_.score.strictCorrect)
    val dispatched = rs.filter(_.dispatched)
    val usage = dispatched.flatMap(_.usage).flatMap(_.totalTokens)
    number(if correct == 0 || usage.isEmpty || usage.size != dispatched.size then None else Some(usage.sum.toDouble / correct))
  private def attemptTokensPerCorrect(rs: Vector[TrialObservation]): String =
    val correct = rs.count(_.score.strictCorrect)
    val attempts = rs.map(_.generationAttempts).sum
    val usage = rs.flatMap(_.attemptUsage).flatMap(_.totalTokens)
    number(if correct == 0 || attempts == 0 || usage.size != attempts then None else Some(usage.sum.toDouble / correct))
  private def writeText(path: Path, text: String): Unit = Files.writeString(path, text, StandardCharsets.UTF_8): Unit
  private def writeCsv(path: Path, header: Vector[String], rows: Vector[Vector[String]]): Unit =
    def line(v: Vector[String]): String = v.map(s => "\"" + s.replace("\"", "\"\"") + "\"").mkString(",")
    require(rows.forall(_.size == header.size), "CSV column count mismatch")
    writeText(path, (header +: rows).map(line).mkString("", "\n", "\n"))
  private def table(header: Vector[String], rows: Vector[Vector[String]]): String =
    def line(v: Vector[String]): String = "| " + v.map(_.replace("|", "\\|").replace("\n", " ")).mkString(" | ") + " |\n"
    line(header) + line(header.map(_ => "---")) + rows.map(line).mkString
  private def fenced(value: String): String =
    val longest = "`+".r.findAllIn(value).map(_.length).foldLeft(0)(math.max)
    val fence = "`" * math.max(3, longest + 1)
    s"$fence\n$value\n$fence\n"
  private def failureExamples(runDir: Path, rows: Vector[TrialObservation]): String =
    val pairs = rows.filter(r => Set("named_end", "generic_end")(r.style)).groupBy(r => (r.familyId, r.task, r.view, r.lexicalRegime, r.replicate)).values.toVector.flatMap { rs =>
      for
        named <- rs.find(r => r.style == "named_end" && r.score.evaluable)
        generic <- rs.find(r => r.style == "generic_end" && r.score.evaluable)
        if !named.score.strictCorrect || !generic.score.strictCorrect
      yield (named, generic)
    }
    val categories = Vector("D correct / B wrong" -> ((true, false)), "D wrong / B correct" -> ((false, true)), "both wrong" -> ((false, false)))
    categories.map { (label, correctness) =>
      val chosen = pairs.filter((n, b) => (n.score.strictCorrect, b.score.strictCorrect) == correctness)
        .sortBy((n, _) => if n.trialHash.nonEmpty then n.trialHash else n.trialId).take(3)
      s"### $label\n\n" + (if chosen.isEmpty then "No eligible paired failures.\n\n" else chosen.map { (n, b) =>
        Vector(n, b).map { r =>
          val lines = r.input.replace("\r\n", "\n").split("\n", -1).toVector
          val input = if lines.size <= 120 then fenced(r.input) else
            val dir = runDir.resolve("failure-inputs")
            Files.createDirectories(dir)
            val filename = r.trialId.replaceAll("[^A-Za-z0-9_-]", "_") + ".txt"
            writeText(dir.resolve(filename), r.input)
            s"Full input: [failure-inputs/$filename](failure-inputs/$filename). Excerpt lines 1–40 of ${lines.size}:\n\n" + fenced(lines.take(40).mkString("\n"))
          val astDiagnostics = if r.task == "source_to_ast" then
            val selected = Vector("ast_error_code", "ast_error_path", "ast_mismatch_class", "ast_mismatch_path").flatMap(k => r.score.diagnostics.get(k).map(v => s"$k=$v"))
            if selected.isEmpty then "" else s"AST diagnostics: ${selected.mkString(", ")}.\n\n"
          else ""
          s"**${r.style}**; trial `${r.trialId}`; outcome `${r.score.outcome.id}`; gold `${r.gold}`.\n\n${astDiagnostics}Input:\n\n$input\nRaw answer:\n\n${fenced(r.rawAnswer)}\n"
        }.mkString
      }.mkString)
    }.mkString
