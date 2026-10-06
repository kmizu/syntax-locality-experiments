package locality.bench

import locality.bench.lang.*
import locality.bench.prompt.AstJson
import locality.bench.score.*
import locality.llm.json.*
import scala.util.control.NonFatal

object ParsingTests:
  private val gold = Program(Vector(
    Stmt.Scope(Kind.UnitScope,"n_abcdefgh",Vector(
      Stmt.Let("x_a",1234),
      Stmt.Scope(Kind.FuncScope,"n_ijklmnop",Vector(Stmt.Let("x_a",5678),Stmt.Nop("p_abcdefgh"))),
      Stmt.Scope(Kind.AreaScope,"n_qrstuvwx",Vector(Stmt.Let("x_b",9999),Stmt.Nop("p_ijklmnop"))),
      Stmt.Nop("p_qrstuvwx")
    )),Stmt.Let("x_f",1000)
  ))
  private val literal = """{"body":[{"type":"scope","kind":"unit","name":"n_abcdefgh","body":[{"type":"let","variable":"x_a","value":1234},{"type":"scope","kind":"func","name":"n_ijklmnop","body":[{"type":"let","variable":"x_a","value":5678},{"type":"nop","payload":"p_abcdefgh"}]},{"type":"scope","kind":"area","name":"n_qrstuvwx","body":[{"type":"let","variable":"x_b","value":9999},{"type":"nop","payload":"p_ijklmnop"}]},{"type":"nop","payload":"p_qrstuvwx"}]},{"type":"let","variable":"x_f","value":1000}]}"""
  private val mini = Program(Vector(Stmt.Let("x_a",1234),Stmt.Nop("p_abcdefgh")))
  private val miniLiteral = """{"body":[{"type":"let","variable":"x_a","value":1234},{"type":"nop","payload":"p_abcdefgh"}]}"""
  private def grade(program: Program, text: String): Score = ParsingGrader.grade(program,GradingResponse("completed",text))
  private def rejected(text: String, code: String, path: String): Unit =
    val error = AstJson.decode(text).swap.toOption.getOrElse(throw new AssertionError("Invalid AST must be rejected"))
    assert(error.code == code,s"Expected $code, got ${error.code}")
    assert(error.path == path,s"Expected $path, got ${error.path}")
    val score = grade(gold,text)
    assert(score.outcome == Outcome.InvalidAnswerFormat && !score.strictCorrect && score.syntaxValid.contains(false))
    assert(score.diagnostics.get("ast_error_code").contains(code) && score.diagnostics.get("ast_error_path").contains(path))
  private def mismatch(program: Program, text: String, category: String, path: String): Unit =
    val score = grade(program,text)
    assert(score.outcome == Outcome.ValidSyntaxWrongAst && !score.strictCorrect && score.syntaxValid.contains(true))
    assert(score.diagnostics.get("ast_mismatch_class").contains(category),s"Expected mismatch $category, got ${score.diagnostics}")
    assert(score.diagnostics.get("ast_mismatch_path").contains(path),s"Expected mismatch path $path, got ${score.diagnostics}")

  def run(): Unit =
    val tests = Vector[(String,() => Unit)](
      "literal full tree preserves scope parents, lexical fields and all three kinds" -> (() => {
        assert(AstJson.decode(literal) == Right(gold))
        val score = grade(gold,literal)
        assert(score.outcome == Outcome.Correct && score.strictCorrect && score.syntaxValid.contains(true))
        val encoded = AstJson.encode(gold)
        assert(Json.canonical(encoded) == Json.canonical(Json.parse(literal).toOption.get))
        assert(AstJson.render(gold) == Json.canonical(Json.parse(literal).toOption.get))
        assert(AstJson.decode(AstJson.render(gold)) == Right(gold))
      }),
      "object field order, whitespace, empty body and integral number spelling are semantic" -> (() => {
        val reordered = """ { "body" : [ { "value":1234.0,"variable":"x_a","type":"let" }, {"payload":"p_abcdefgh","type":"nop"} ] } """
        assert(AstJson.decode(reordered) == Right(mini) && grade(mini,reordered).strictCorrect)
        assert(AstJson.decode("{\"body\":[]}") == Right(Program(Vector.empty)))
        assert(AstJson.render(Program(Vector.empty)) == "{\"body\":[]}")
        assert(AstJson.decode("""{"body":[{"type":"let","variable":"x_f","value":1e3}]}""") == Right(Program(Vector(Stmt.Let("x_f",1000)))))
      }),
      "valid wrong kind, name, variable, value, payload and node type have deterministic paths" -> (() => {
        mismatch(gold,literal.replace("\"kind\":\"unit\"","\"kind\":\"area\""),"kind","$.body[0].kind")
        mismatch(gold,literal.replace("n_abcdefgh","n_zzzzzzzz"),"name","$.body[0].name")
        mismatch(gold,literal.replace("\"variable\":\"x_a\",\"value\":1234","\"variable\":\"x_c\",\"value\":1234"),"variable","$.body[0].body[0].variable")
        mismatch(gold,literal.replace("\"value\":1234","\"value\":1235"),"value","$.body[0].body[0].value")
        mismatch(gold,literal.replace("p_abcdefgh","p_zzzzzzzz"),"payload","$.body[0].body[1].body[1].payload")
        mismatch(gold,literal.replace("{\"type\":\"nop\",\"payload\":\"p_abcdefgh\"}","{\"type\":\"let\",\"variable\":\"x_c\",\"value\":4567}"),"node_type","$.body[0].body[1].body[1].type")
      }),
      "statement and sibling scope order never pass strict AST equality" -> (() => {
        mismatch(mini,"""{"body":[{"type":"nop","payload":"p_abcdefgh"},{"type":"let","variable":"x_a","value":1234}]}""","order","$.body")
        val siblings = Program(Vector(Stmt.Scope(Kind.FuncScope,"n_abcdefgh",Vector.empty),Stmt.Scope(Kind.AreaScope,"n_ijklmnop",Vector.empty)))
        mismatch(siblings,"""{"body":[{"type":"scope","kind":"area","name":"n_ijklmnop","body":[]},{"type":"scope","kind":"func","name":"n_abcdefgh","body":[]}]}""","order","$.body")
      }),
      "moving a let to another parent changes the full tree" -> (() => {
        val nested = Program(Vector(Stmt.Scope(Kind.UnitScope,"n_abcdefgh",Vector(Stmt.Scope(Kind.FuncScope,"n_ijklmnop",Vector(Stmt.Let("x_a",1234)))))))
        val wrongParent = """{"body":[{"type":"scope","kind":"unit","name":"n_abcdefgh","body":[{"type":"let","variable":"x_a","value":1234},{"type":"scope","kind":"func","name":"n_ijklmnop","body":[]}]}]}"""
        mismatch(nested,wrongParent,"node_type","$.body[0].body[0].type")
      }),
      "missing and extra statements are structural mismatches" -> (() => {
        mismatch(mini,"""{"body":[{"type":"let","variable":"x_a","value":1234}]}""","missing_child","$.body[1]")
        mismatch(mini,"""{"body":[{"type":"let","variable":"x_a","value":1234},{"type":"nop","payload":"p_abcdefgh"},{"type":"nop","payload":"p_ijklmnop"}]}""","extra_child","$.body[2]")
      }),
      "malformed JSON, duplicate JSON fields, extra prose and probe nodes are invalid" -> (() => {
        Vector("{\"body\":[}","{\"body\":[],}","{\"body\":[]} trailing","{\"body\":[],\"body\":[]}","{\"body\":[{\"type\":\"let\",\"variable\":\"x_a\",\"value\":1234,\"value\":5678}]}").foreach(rejected(_,"invalid_json","$"))
        rejected("{\"body\":[{\"type\":\"probe\"}]}","invalid_node_type","$.body[0].type")
      }),
      "exact schemas reject unknown, missing, null and wrong-type fields" -> (() => {
        rejected("{\"body\":[],\"extra\":true}","unknown_field","$.extra")
        rejected("{}","missing_field","$.body")
        rejected("[]","type_mismatch","$")
        rejected("{\"body\":null}","type_mismatch","$.body")
        rejected("{\"body\":[null]}","type_mismatch","$.body[0]")
        rejected("{\"body\":[{\"type\":123}]}","type_mismatch","$.body[0].type")
        rejected("{\"body\":[{\"type\":\"scope\",\"kind\":\"unit\",\"name\":\"n_abcdefgh\"}]}","missing_field","$.body[0].body")
        rejected("{\"body\":[{\"type\":\"let\",\"variable\":\"x_a\",\"value\":1234,\"body\":[]}]}","unknown_field","$.body[0].body")
        rejected("{\"body\":[{\"type\":\"nop\",\"payload\":false}]}","type_mismatch","$.body[0].payload")
      }),
      "kind, name, variable, payload and four-digit integer constraints are strict" -> (() => {
        rejected("{\"body\":[{\"type\":\"scope\",\"kind\":\"class\",\"name\":\"n_abcdefgh\",\"body\":[]}]}","unknown_kind","$.body[0].kind")
        Vector("n_abcdefg","n_abcdefghi","n_Abcdefgh","abcdefgh","n_abcdeあfg").foreach { name =>
          rejected(s"""{"body":[{"type":"scope","kind":"unit","name":"$name","body":[]}]}""","invalid_name","$.body[0].name")
        }
        Vector("x_g","x_aa","x_A","a").foreach { variable =>
          rejected(s"""{"body":[{"type":"let","variable":"$variable","value":1234}]}""","invalid_variable","$.body[0].variable")
        }
        Vector("p_abcdefg","p_abcdefghi","p_Abcdefgh","abcdefgh").foreach { payload =>
          rejected(s"""{"body":[{"type":"nop","payload":"$payload"}]}""","invalid_payload","$.body[0].payload")
        }
        Vector("999","10000","-1234","1234.5","9223372036854775808").foreach { number =>
          rejected(s"""{"body":[{"type":"let","variable":"x_a","value":$number}]}""","invalid_value","$.body[0].value")
        }
        rejected("{\"body\":[{\"type\":\"let\",\"variable\":\"x_a\",\"value\":\"1234\"}]}","type_mismatch","$.body[0].value")
      }),
      "duplicate block names are global, duplicate lets local, and nested shadowing is allowed" -> (() => {
        rejected("""{"body":[{"type":"scope","kind":"unit","name":"n_abcdefgh","body":[{"type":"scope","kind":"func","name":"n_abcdefgh","body":[]}]}]}""","duplicate_name","$.body[0].body[0].name")
        rejected("""{"body":[{"type":"scope","kind":"unit","name":"n_abcdefgh","body":[]},{"type":"scope","kind":"area","name":"n_abcdefgh","body":[]}]}""","duplicate_name","$.body[1].name")
        rejected("""{"body":[{"type":"let","variable":"x_a","value":1234},{"type":"let","variable":"x_a","value":5678}]}""","duplicate_variable","$.body[1].variable")
        assert(AstJson.decode(literal) == Right(gold),"x_a in a nested function must preserve legitimate shadowing")
      }),
      "one whole fence is auxiliary only and commentary or embedded fences stay invalid" -> (() => {
        val fenced = "```json\n" + miniLiteral + "\n```"
        val score = grade(mini,fenced)
        assert(score.outcome == Outcome.InvalidAnswerFormat && !score.strictCorrect && score.auxiliaryCorrect.contains(true) && score.syntaxValid.contains(false))
        val commentary = grade(mini,"Here is the AST:\n" + fenced)
        assert(!commentary.strictCorrect && commentary.auxiliaryCorrect.isEmpty)
        assert(grade(mini,"```json\n```text\n" + miniLiteral + "\n```\n```").auxiliaryCorrect.isEmpty)
      }),
      "refusal, incomplete and infrastructure keep existing status and syntax policy" -> (() => {
        val incomplete = ParsingGrader.grade(gold,GradingResponse("incomplete",literal))
        assert(incomplete.outcome == Outcome.IncompleteOutput && !incomplete.strictCorrect && incomplete.evaluable && incomplete.auxiliaryCorrect.contains(true) && incomplete.syntaxValid.contains(false))
        for response <- Vector(GradingResponse("refusal",literal),GradingResponse("completed",literal,true)) do
          val refusal = ParsingGrader.grade(gold,response)
          assert(refusal.outcome == Outcome.Refusal && !refusal.strictCorrect && refusal.evaluable && refusal.syntaxValid.contains(false))
        for status <- Vector("api_rejected","transport_failure","ambiguous_outcome","decode_failure","not_dispatched") do
          val missing = ParsingGrader.grade(gold,GradingResponse(status,"not JSON"))
          assert(!missing.evaluable && !missing.strictCorrect && missing.syntaxValid.isEmpty && missing.auxiliaryCorrect.isEmpty)
      }),
      "complete trees at depth16 and32 stay within JSON128 and preserve every ancestor" -> (() => {
        for depth <- Vector(16,32) do
          var expected = Vector[Stmt](Stmt.Let("x_f",5916))
          var body = "[{\"type\":\"let\",\"variable\":\"x_f\",\"value\":5916}]"
          for index <- (0 until depth).reverse do
            val name = "n_aaaaaa" + ('a'+index/26).toChar + ('a'+index%26).toChar
            expected = Vector(Stmt.Scope(Kind.UnitScope,name,expected))
            body = s"""[{"type":"scope","kind":"unit","name":"$name","body":$body}]"""
          val text = "{\"body\":" + body + "}"
          val program = Program(expected)
          assert(AstJson.decode(text) == Right(program),s"Full depth$depth tree must parse")
          assert(grade(program,text).strictCorrect)
          assert(AstJson.decode(AstJson.render(program)) == Right(program))
      })
    )
    val failures = Vector.newBuilder[String]
    tests.foreach { (name,test) =>
      try { test(); println(s"PASS parsing: $name") }
      catch case NonFatal(error) =>
        failures += s"$name: ${error.getClass.getSimpleName}: ${error.getMessage}"
        println(s"FAIL parsing: $name")
    }
    val errors = failures.result()
    assert(errors.isEmpty,errors.mkString("\n"))
    println(s"Passed ${tests.size} parsing cases")
  def main(args: Array[String]): Unit =
    require(args.isEmpty,"ParsingTests accepts no arguments")
    run()
