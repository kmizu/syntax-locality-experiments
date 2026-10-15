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
  val version = "prompt-v1"
  def describe(test: ExpandedCase, examples: Vector[ExpandedCase]): BuiltPrompt =
    require(examples.forall(e => e.task == test.task && e.view == test.view && e.spec == test.spec), "Examples must match task, view, and grammar")
    // Import sentences appear only in import plans, so import-free prompts are byte-identical to the original ones.
    val imports = test.family.imports
    require(examples.forall(_.family.imports == imports), "Examples must share the test's import condition")
    val grammar = syntax(test.spec, imports)
    def table(e: ExpandedCase): String = AstTable.render(e.family.program, Seeds.derive(e.family.structureSeed, "table", 0))
    val exampleText = examples.zipWithIndex.map { (example, i) =>
      val question = if test.task == Task.AstToSource then "AST table:\n" + table(example) + "Program:\n" else "Program prefix:\n" + example.source + "Answer:\n"
      val answer = test.task match
        case Task.ScopeLookup => example.goldObservation.value.toString + "\n"
        case Task.ActiveStack => example.goldObservation.activeScopes.map(n => s"\"$n\"").mkString("[", ",", "]\n")
        case Task.AstToSource => example.goldSource
      s"Example ${i + 1}\n$question$answer"
    }.mkString("\n")
    val source = if test.task == Task.AstToSource then table(test) else test.source
    val testText = if test.task == Task.AstToSource then "AST table:\n" + source + "Program:\n" else "Program prefix:\n" + source + "Answer:\n"
    val text = introduction(test.task, imports) + "\nSYNTAX\n" + grammar + "\nEXAMPLES\n" + exampleText + "\nTEST\n" + testText
    def byteCount(s: String): Int = s.getBytes(UTF_8).length
    val sourceLines = source.linesIterator.toVector
    def offset(lineIndex: Int): Option[SourceOffset] =
      if lineIndex < 0 || lineIndex >= sourceLines.size then None
      else
        val start = sourceLines.take(lineIndex).map(_.length + 1).sum
        Some(SourceOffset(lineIndex + 1, start, byteCount(source.take(start))))
    def findLine(predicate: String => Boolean): Option[SourceOffset] = offset(sourceLines.indexWhere(l => predicate(l.trim)))
    val reading = test.task != Task.AstToSource
    val indentStyle = test.spec.style == SyntaxStyle.Indent
    // Indent-style closes emit no line, so an event index maps to the line index minus the preceding closes.
    val programEvents = Generator.events(test.family.program)
    def eventOffset(eventIndex: Int): Option[SourceOffset] =
      val closes = if indentStyle then programEvents.take(eventIndex).count(_ == ParsedEvent.Close) else 0
      offset(eventIndex - closes)
    val opener = if reading then findLine(l => l.split("[ \\t]+").toVector.lift(1).contains(test.family.focusedName)) else None
    // Locate the relevant binding from generator metadata and structure, without consulting test gold.
    val binding = if reading then
      val owner = if test.view.contains(View.BeforeClose) then Some(test.family.focusedName) else test.family.visibleOwner
      // Direct let variables of each block, so an import line counts as the binding when its target binds the variable.
      def directLets(body: Vector[Stmt]): Map[String, Set[String]] = body.flatMap {
        case Stmt.Scope(_, name, children) => directLets(children) + (name -> children.collect { case Stmt.Let(v, _) => v }.toSet)
        case _ => Map.empty[String, Set[String]]
      }.toMap
      val lets = directLets(test.family.program.body)
      var active = Vector.empty[String]
      var bindingIndex = -1
      programEvents.take(test.family.cut(test.view.get).eventCount).zipWithIndex.foreach { (event, i) =>
        event match
          case ParsedEvent.Open(_, n) => active :+= n
          case ParsedEvent.Close => active = active.dropRight(1)
          case ParsedEvent.Let(v, _) if v == test.family.targetVariable && active.lastOption == owner => bindingIndex = i
          case ParsedEvent.Import(t) if active.lastOption == owner && lets.getOrElse(t, Set.empty).contains(test.family.targetVariable) => bindingIndex = i
          case _ => ()
      }
      if bindingIndex < 0 then None else eventOffset(bindingIndex)
    else None
    // The indent style has no closing lines, so its close span does not exist.
    val closeStart = if reading && !indentStyle && test.view.contains(View.AfterClose) then eventOffset(test.family.before.eventCount) else None
    // Span endpoints are exclusive: the close sequence ends at the beginning of probe.
    val closeEnd = if reading && !indentStyle && test.view.contains(View.AfterClose) then eventOffset(test.family.after.eventCount) else None
    val probe = if reading then findLine(_.startsWith("probe ")) else None
    val metrics = PromptMetrics(grammar.length, byteCount(grammar), exampleText.length, byteCount(exampleText),
      source.length, byteCount(source), sourceLines.size, source.length, byteCount(source), opener, binding, closeStart, closeEnd, probe)
    BuiltPrompt(text, metrics)
  def build(test: ExpandedCase, examples: Vector[ExpandedCase], model: String,
    reasoningEffort: Option[String], maxOutputTokens: Int): LlmRequest =
    require(maxOutputTokens > 0, "output token limit must be positive")
    LlmRequest(model, "Follow the task and return only the requested answer, without explanation or Markdown fences.",
      Vector(TextMessage("user", describe(test, examples).text)), reasoningEffort, maxOutputTokens)

  /** Import semantics, stated once in each task introduction of an import plan. */
  val importSemantics: String =
    "An import statement brings the let bindings made directly inside the named block into the current scope.\n" +
      "The named block must already be closed where the import appears; importing an open block or a block that closes later is an error.\n" +
      "A variable is resolved by the current scope's own let bindings first, then by that scope's imports with the later import winning, then by the enclosing scopes outward in the same way.\n"

  private def introduction(task: Task, imports: Boolean): String = task match
    case Task.ScopeLookup | Task.ActiveStack =>
      val answer = if task == Task.ScopeLookup then "Return only the decimal integer visible at the final probe.\n"
        else "Return only a JSON array of the names of still-open blocks, ordered from outermost to innermost. Do not include the implicit global scope. If no blocks remain open, return [].\n"
      "You will read a prefix of a small scope language.\n" +
        "Every block, regardless of its kind, creates a local scope.\n" +
        "Statements are processed once, from top to bottom.\n" +
        "A let statement binds a variable in the current scope.\n" +
        "The nearest still-open scope containing the variable determines its value.\n" +
        "Bindings in closed scopes are no longer visible.\n" +
        (if imports then importSemantics else "") +
        "A nop statement has no effect.\n" +
        "The final probe marks the observation point.\n" +
        "Open blocks at the end of this prefix are intentional.\n" +
        "Do not complete or repair the program.\n" + answer
    case Task.AstToSource =>
      "Translate the neutral AST table into a complete program in the following scope language.\n" +
        "Every block, regardless of its kind, creates a local scope.\n" +
        "ROOT is the implicit global scope and produces no source line. SCOPE opens a block and must be closed after its children.\n" +
        "LET binds its variable to its value. NOP produces a no-effect statement with its payload.\n" +
        (if imports then "IMPORT produces an import statement whose target is the block given in its name field.\n" + importSemantics else "") +
        "Each node's parent identifies its containing body. The order field gives the statement order within that body.\n" +
        "Rows are in parent-before-child order; their presentation order does not give statement order.\n" +
        "Node IDs are references for the table only and must not appear in source.\n" +
        "Use the grammar's keywords for the table's semantic kinds unit, func, and area.\n" +
        "Return only the complete program, without Markdown fences or explanation.\n"

  /** The qualification-specific import grammar sentence; IMPORT is the lexicon's import keyword. */
  def importSyntax(spec: RenderSpec): String = spec.qualification match
    case Qualification.Short => s"An import statement is ${spec.lexicon.importWord} NAME, where NAME is the name of a block that has already been closed.\n"
    case Qualification.Fq => s"An import statement is ${spec.lexicon.importWord} PATH, where PATH is the names of every block enclosing the imported block, from the top level inward, followed by the imported block's own name, joined with dots; the imported block must already be closed.\n"

  private def syntax(spec: RenderSpec, imports: Boolean): String =
    val l = spec.lexicon
    val kinds = s"The kind keywords are ${l.unit} for unit, ${l.func} for func, and ${l.area} for area. Each creates a local scope.\n"
    val opening = if spec.style == SyntaxStyle.Braces then "Open a block with KIND NAME { on one line.\n" else "Open a block with KIND NAME on one line.\n"
    val closing = spec.style match
      case SyntaxStyle.Braces => "Close the innermost open block with } on its own line.\n"
      case SyntaxStyle.GenericEnd => s"Close the innermost open block with ${l.end} on its own line.\n"
      case SyntaxStyle.TypedEnd => s"Close the innermost open block with ${l.end} KIND on one line. KIND must repeat that block's opening kind keyword.\n"
      case SyntaxStyle.NamedEnd => s"Close the innermost open block with ${l.end} KIND NAME on one line. KIND and NAME must repeat that block's opening keyword and name.\n"
      case SyntaxStyle.PaddedEnd => s"Close the innermost open block with ${l.end} junk z_aaaaaaaa on one line. The suffix junk z_aaaaaaaa is identical for every block and carries no scope information.\n"
      case SyntaxStyle.DepthEnd => s"Close the innermost open block with ${l.end} K on one line. K is the nesting depth of the block being closed: 1 for a block opened at top level, 2 for a block opened directly inside a depth 1 block, and so on.\n"
      case SyntaxStyle.Indent => "Blocks have no closing line; see the indentation rule below.\n"
      case SyntaxStyle.IndentNamedEnd => s"Close the innermost open block with ${l.end} KIND NAME on its own line, indented exactly like that block's opening line. KIND and NAME must repeat the block's opening keyword and name.\n"
      case SyntaxStyle.IndentGenericEnd => s"Close the innermost open block with ${l.end} on its own line, indented exactly like that block's opening line.\n"
    val layout = spec.style match
      case SyntaxStyle.Indent => "Indentation is significant. Each line inside a block is indented by exactly two more spaces than the line that opened the block; top-level lines are not indented. A block ends when a line with less indentation appears or when the program ends. There is no closing line.\n"
      case SyntaxStyle.IndentNamedEnd | SyntaxStyle.IndentGenericEnd => "Indentation is significant. Each line inside a block is indented by exactly two more spaces than the line that opened the block; top-level lines are not indented. The closing line of a block uses the same indentation as its opening line, and indentation and closing lines must agree.\n"
      case _ => "Indentation has no meaning; scope is determined solely by explicit opening and closing lines.\n"
    kinds + opening + closing +
      s"KIND is one of ${l.unit}, ${l.func}, ${l.area}; NAME is n_ followed by exactly eight lowercase letters and is unique in the program.\n" +
      s"A binding statement (let) is ${l.let} VARIABLE VALUE. VARIABLE is x_ followed by one letter a through f; VALUE is a four digit integer from 1000 through 9999.\n" +
      "Within one scope a variable is bound at most once. Inner bindings hide outer bindings until their scope closes.\n" +
      (if imports then importSyntax(spec) else "") +
      s"A no-effect statement (nop) is ${l.nop} PAYLOAD. PAYLOAD is p_ followed by eight lowercase letters.\n" +
      "The observation marker is probe PROBE_ID VARIABLE. PROBE_ID is q_ followed by eight lowercase letters.\n" +
      "Use one statement per line. " + layout
