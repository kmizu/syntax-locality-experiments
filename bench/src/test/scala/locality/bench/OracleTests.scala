package locality.bench

import locality.bench.lang.*

object OracleTests:
  val example = Program(Vector(
    Stmt.Let("x_a", 1048),
    Stmt.Scope(Kind.UnitScope, "n_qazwsxed", Vector(
      Stmt.Let("x_a", 7321),
      Stmt.Scope(Kind.FuncScope, "n_plmoknij", Vector(
        Stmt.Let("x_a", 5916),
        Stmt.Scope(Kind.AreaScope, "n_uhbygvtf", Vector(
          Stmt.Let("x_a", 8604), Stmt.Nop("p_abcdefgh")))
      ))
    ))
  ))

  def run(): Unit =
    assert(ReferenceOracle.observe(example, CutPoint(9, "q_zxcvbnma", "x_a")) ==
      Right(Observation(5916, Vector("n_qazwsxed", "n_plmoknij"))), "handmade shadowing after close")
    assert(ReferenceOracle.observe(example, CutPoint(8, "q_zxcvbnma", "x_a")) ==
      Right(Observation(8604, Vector("n_qazwsxed", "n_plmoknij", "n_uhbygvtf"))), "before close")
    assert(ReferenceOracle.observe(example, CutPoint(11, "q_zxcvbnma", "x_a")) ==
      Right(Observation(1048, Vector.empty)), "global restored")
    assert(ReferenceOracle.observe(example, CutPoint(-1, "q_zxcvbnma", "x_a")).isLeft)
    assert(ReferenceOracle.observe(example, CutPoint(12, "q_zxcvbnma", "x_a")).isLeft)
    assert(ReferenceOracle.observe(example, CutPoint(0, "q_zxcvbnma", "x_a")).isLeft)
    val duplicate = Program(Vector(Stmt.Let("x_a", 1000), Stmt.Let("x_a", 2000)))
    assert(ReferenceOracle.observe(duplicate, CutPoint(2, "q_zxcvbnma", "x_a")).isLeft)
    val sibling = Program(Vector(Stmt.Let("x_a", 3000),
      Stmt.Scope(Kind.AreaScope, "n_abcdefgh", Vector(Stmt.Let("x_a", 7000))),
      Stmt.Nop("p_abcdefgh")))
    assert(ReferenceOracle.observe(sibling, CutPoint(5, "q_abcdefgh", "x_a")) == Right(Observation(3000, Vector.empty)))
    assert(EventOracle.observe(ParsedPrefix(Vector(ParsedEvent.Close), "q_abcdefgh", "x_a")).isLeft)
    assert(EventOracle.observe(ParsedPrefix(Vector(ParsedEvent.Open(Kind.FuncScope, "n_abcdefgh"), ParsedEvent.Let("x_a", 1234)), "q_abcdefgh", "x_a")) == Right(Observation(1234, Vector("n_abcdefgh"))))
    assert(EventOracle.observe(ParsedPrefix(Vector(ParsedEvent.Nop("p_abcdefgh")), "q_abcdefgh", "x_a")).isLeft)

  def main(args: Array[String]): Unit = run()
