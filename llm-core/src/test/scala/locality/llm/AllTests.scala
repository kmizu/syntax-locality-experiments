package locality.llm

object TestHarness:
  private var tests = Vector.empty[(String, String, () => Unit)]
  def test(suite: String, name: String)(body: => Unit): Unit =
    tests :+= (suite, name, () => body)
  def run(args: Array[String]): Unit =
    val suite = args.toVector match
      case Vector() => None
      case Vector("--suite", value) => Some(value)
      case _ => throw new IllegalArgumentException("Expected --suite NAME or no options")
    val selected = tests.filter(t => suite.forall(_ == t._1))
    require(selected.nonEmpty, "No tests selected")
    var failures = Vector.empty[String]
    selected.foreach { (s, n, body) =>
      try
        body()
        println(s"PASS $s: $n")
      catch
        case e: Throwable =>
          failures :+= s"$s: $n: ${e.getClass.getSimpleName}: ${e.getMessage}"
          println(s"FAIL ${failures.last}")
    }
    println(s"${selected.size - failures.size}/${selected.size} tests passed")
    if failures.nonEmpty then throw new AssertionError(failures.mkString("\n"))

object AllTests:
  def main(args: Array[String]): Unit =
    JsonTests.register()
    ResponseTests.register()
    HttpTests.register()
    TestHarness.run(args)

object CoreTests:
  def main(args: Array[String]): Unit = AllTests.main(args)
