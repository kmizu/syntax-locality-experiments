package locality.bench.prompt

import locality.bench.data.*
import locality.bench.lang.*
import locality.llm.*
import java.nio.charset.StandardCharsets.UTF_8

final case class SourceOffset(line: Int, charOffset: Int, byteOffset: Int)
final case class PromptMetrics(grammarChars: Int, grammarBytes: Int, examplesChars: Int, examplesBytes: Int,
  testChars: Int, testBytes: Int, sourceLines: Int, sourceChars: Int, sourceBytes: Int,
  opener: Option[SourceOffset], binding: Option[SourceOffset], closeStart: Option[SourceOffset],
  closeEnd: Option[SourceOffset], probe: Option[SourceOffset])
final case class BuiltPrompt(text: String, metrics: PromptMetrics)

object PromptBuilder:
  val version = "prompt-v2-full-program"
  def describe(test: ExpandedCase, examples: Vector[ExpandedCase]): BuiltPrompt =
    require(examples.forall(e => e.task == test.task && e.view == test.view && e.spec == test.spec), "Examples must match task, view, and grammar")
    val grammar = syntax(test.spec)
    def table(e: ExpandedCase): String = AstTable.render(e.family.program, Seeds.derive(e.family.structureSeed, "table", 0))
    def question(e: ExpandedCase): String = e.task match
      case Task.AstToSource => "AST table:\n" + table(e) + "Program:\n"
      case Task.SourceToAst => "Program:\n" + e.source + "AST:\n"
      case _ => "Program prefix:\n" + e.source + "Answer:\n"
    val exampleText = examples.zipWithIndex.map { (example, i) =>
      val answer = test.task match
        case Task.ScopeLookup => example.goldObservation.value.toString + "\n"
        case Task.ActiveStack => example.goldObservation.activeScopes.map(n => s"\"$n\"").mkString("[", ",", "]\n")
        case Task.AstToSource => example.goldSource
        case Task.SourceToAst => AstJson.render(example.family.program) + "\n"
      s"Example ${i + 1}\n${question(example)}$answer"
    }.mkString("\n")
    val source = if test.task == Task.AstToSource then table(test) else test.source
    val testText = question(test)
    val text = introduction(test.task) + "\nSYNTAX\n" + grammar + "\nEXAMPLES\n" + exampleText + "\nTEST\n" + testText
    def byteCount(s: String): Int = s.getBytes(UTF_8).length
    val sourceLines = source.linesIterator.toVector
    def offset(lineIndex: Int): Option[SourceOffset] =
      if lineIndex < 0 || lineIndex >= sourceLines.size then None
      else
        val start = sourceLines.take(lineIndex).map(_.length + 1).sum
        Some(SourceOffset(lineIndex + 1, start, byteCount(source.take(start))))
    def findLine(predicate: String => Boolean): Option[SourceOffset] = offset(sourceLines.indexWhere(predicate))
    val reading = !test.task.isFullProgram
    val opener = if reading then findLine(l => l.split("[ \\t]+").toVector.lift(1).contains(test.family.focusedName)) else None
    // Locate the relevant binding from generator metadata and structure, without consulting test gold.
    val binding = if reading then
      val owner = if test.view.contains(View.BeforeClose) then Some(test.family.focusedName) else test.family.visibleOwner
      var active = Vector.empty[String]
      var bindingIndex = -1
      Generator.events(test.family.program).take(test.family.cut(test.view.get).eventCount).zipWithIndex.foreach { (event, i) =>
        event match
          case ParsedEvent.Open(_, n) => active :+= n
          case ParsedEvent.Close => active = active.dropRight(1)
          case ParsedEvent.Let(v, _) if v == test.family.targetVariable && active.lastOption == owner => bindingIndex = i
          case _ => ()
      }
      offset(bindingIndex)
    else None
    val closeStart = if reading && test.view.contains(View.AfterClose) then offset(test.family.before.eventCount) else None
    // Span endpoints are exclusive: the close sequence ends at the beginning of probe.
    val closeEnd = if reading && test.view.contains(View.AfterClose) then offset(test.family.after.eventCount) else None
    val probe = if reading then findLine(_.startsWith("probe ")) else None
    val metrics = PromptMetrics(grammar.length, byteCount(grammar), exampleText.length, byteCount(exampleText),
      source.length, byteCount(source), sourceLines.size, source.length, byteCount(source), opener, binding, closeStart, closeEnd, probe)
    BuiltPrompt(text, metrics)
  def build(test: ExpandedCase, examples: Vector[ExpandedCase], model: String,
    reasoningEffort: Option[String], maxOutputTokens: Int): LlmRequest =
    require(maxOutputTokens > 0, "output token limit must be positive")
    LlmRequest(model, "Follow the task and return only the requested answer, without explanation or Markdown fences.",
      Vector(TextMessage("user", describe(test, examples).text)), reasoningEffort, maxOutputTokens)

  private def introduction(task: Task): String = task match
    case Task.ScopeLookup | Task.ActiveStack =>
      val answer = if task == Task.ScopeLookup then "Return only the decimal integer visible at the final probe.\n"
        else "Return only a JSON array of the names of still-open blocks, ordered from outermost to innermost. Do not include the implicit global scope. If no blocks remain open, return [].\n"
      "You will read a prefix of a small scope language.\n" +
        "Every block, regardless of its kind, creates a local scope.\n" +
        "Statements are processed once, from top to bottom.\n" +
        "A let statement binds a variable in the current scope.\n" +
        "The nearest still-open scope containing the variable determines its value.\n" +
        "Bindings in closed scopes are no longer visible.\n" +
        "A nop statement has no effect.\n" +
        "The final probe marks the observation point.\n" +
        "Open blocks at the end of this prefix are intentional.\n" +
        "Do not complete or repair the program.\n" + answer
    case Task.AstToSource =>
      "Translate the neutral AST table into a complete program in the following scope language.\n" +
        "Every block, regardless of its kind, creates a local scope.\n" +
        "ROOT is the implicit global scope and produces no source line. SCOPE opens a block and must be closed after its children.\n" +
        "LET binds its variable to its value. NOP produces a no-effect statement with its payload.\n" +
        "Each node's parent identifies its containing body. The order field gives the statement order within that body.\n" +
        "Rows are in parent-before-child order; their presentation order does not give statement order.\n" +
        "Node IDs are references for the table only and must not appear in source.\n" +
        "Use the grammar's keywords for the table's semantic kinds unit, func, and area.\n" +
        "Return only the complete program, without Markdown fences or explanation.\n"
    case Task.SourceToAst =>
      "Parse the complete program into its neutral JSON abstract syntax tree.\n" +
        "Preserve every node, parent-child relationship, statement order, scope kind and name, variable and value, and nop payload.\n" +
        "Do not evaluate bindings or discard shadowed bindings or nop statements.\n" +
        "The root is a JSON object with exactly one field, body, containing an ordered array of statements.\n" +
        "A scope node has exactly these fields: {\"type\":\"scope\",\"kind\":\"func\",\"name\":\"n_abcdefgh\",\"body\":[]}.\n" +
        "Its kind is the semantic category unit, func, or area, using the mapping in SYNTAX even when the input keywords differ.\n" +
        "A binding node has exactly these fields: {\"type\":\"let\",\"variable\":\"x_a\",\"value\":1739}.\n" +
        "A nop node has exactly these fields: {\"type\":\"nop\",\"payload\":\"p_abcdefgh\"}.\n" +
        "Use the actual names, variables, integer values and payloads from the input, not these illustrative schema values.\n" +
        "Closing lines determine the end of a scope body and do not create AST nodes.\n" +
        "Object field order and whitespace may vary; body-array order must match the source statement order.\n" +
        "Return only the complete JSON AST, without Markdown fences or explanation.\n"

  private def syntax(spec: RenderSpec): String =
    val l = spec.lexicon
    val kinds = s"The kind keywords are ${l.unit} for unit, ${l.func} for func, and ${l.area} for area. Each creates a local scope.\n"
    val opening = if spec.style == SyntaxStyle.Braces then "Open a block with KIND NAME { on one line.\n" else "Open a block with KIND NAME on one line.\n"
    val closing = spec.style match
      case SyntaxStyle.Braces => "Close the innermost open block with } on its own line.\n"
      case SyntaxStyle.GenericEnd => s"Close the innermost open block with ${l.end} on its own line.\n"
      case SyntaxStyle.TypedEnd => s"Close the innermost open block with ${l.end} KIND on one line. KIND must repeat that block's opening kind keyword.\n"
      case SyntaxStyle.NamedEnd => s"Close the innermost open block with ${l.end} KIND NAME on one line. KIND and NAME must repeat that block's opening keyword and name.\n"
      case SyntaxStyle.PaddedEnd => s"Close the innermost open block with ${l.end} junk z_aaaaaaaa on one line. The suffix junk z_aaaaaaaa is identical for every block and carries no scope information.\n"
    kinds + opening + closing +
      s"KIND is one of ${l.unit}, ${l.func}, ${l.area}; NAME is n_ followed by exactly eight lowercase letters and is unique in the program.\n" +
      s"A binding statement (let) is ${l.let} VARIABLE VALUE. VARIABLE is x_ followed by one letter a through f; VALUE is a four digit integer from 1000 through 9999.\n" +
      "Within one scope a variable is bound at most once. Inner bindings hide outer bindings until their scope closes.\n" +
      s"A no-effect statement (nop) is ${l.nop} PAYLOAD. PAYLOAD is p_ followed by eight lowercase letters.\n" +
      "The observation marker is probe PROBE_ID VARIABLE. PROBE_ID is q_ followed by eight lowercase letters.\n" +
      "Use one statement per line. Indentation has no meaning; scope is determined solely by explicit opening and closing lines.\n"
