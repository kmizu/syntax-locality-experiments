package locality.bench

import locality.bench.lang.*
import locality.bench.prompt.AstTable
import locality.bench.score.*

object TableTests:
  def run(): Unit =
    val p = OracleTests.example
    val table = AstTable.render(p, 900L)
    assert(table.startsWith("node\tparent\torder\ttag\tkind\tname\tvariable\tvalue\tpayload\n"), "neutral table schema")
    assert(!table.contains("{"))
    assert(AstTable.parse(table) == Right(p), "table round trip")
    assert(AstTable.parse(AstTable.render(p, 901L)) == Right(p), "node ID change preserves semantics")
    val alteredValue = table.replace("5916", "5917")
    assert(AstTable.parse(alteredValue).toOption.get != p)
    assert(AstTable.parse(table + table.linesIterator.drop(1).next() + "\n").isLeft, "duplicate node ID")
    assert(AstTable.parse(table.replace("x_a", "x_g")).isLeft)
    val ordered = Program(Vector(Stmt.Scope(Kind.FuncScope, "n_abcdefgh", Vector(Stmt.Let("x_a", 1234), Stmt.Nop("p_abcdefgh")))))
    val reordered = Program(Vector(Stmt.Scope(Kind.FuncScope, "n_abcdefgh", Vector(Stmt.Nop("p_abcdefgh"), Stmt.Let("x_a", 1234)))))
    val orderedTable = AstTable.render(ordered, 902L)
    // Keep node IDs, parent links and physical row order fixed; swap sibling ordinals.
    val reorderedTable = orderedTable.linesIterator.map { line =>
      val fields = line.split("\t", -1).toVector
      if Set("LET", "NOP").contains(fields(3)) then fields.updated(2, (1 - fields(2).toInt).toString).mkString("\t") else line
    }.mkString("", "\n", "\n")
    val decoded = AstTable.parse(reorderedTable).toOption.get
    assert(decoded == reordered && decoded != ordered, "Explicit sibling order must determine the decoded AST")
    SyntaxStyle.values.foreach { style =>
      val spec = RenderSpec(style, Lexicon.natural)
      val generated = Renderer.renderProgram(reordered, spec)
      assert(Parser.parseProgram(generated, spec) == Right(decoded))
      assert(GenerationGrader.grade(decoded, GradingResponse("completed", generated), spec).strictCorrect)
      assert(GenerationGrader.grade(ordered, GradingResponse("completed", generated), spec).outcome == Outcome.ValidSyntaxWrongAst)
    }
  def main(args: Array[String]): Unit = run()
