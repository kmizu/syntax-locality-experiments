package locality.bench.prompt

import locality.bench.data.*
import locality.bench.lang.*
import java.util.Random

object Examples:
  val version = "examples-v1"
  private val cache = scala.collection.mutable.Map.empty[(Long, Task, Int), Vector[CaseFamily]]
  def forCase(test: ExpandedCase, seed: Long, fewShotCount: Int = 8): Vector[ExpandedCase] =
    require(fewShotCount >= 0 && fewShotCount <= 64, "few-shot count must be 0..64")
    val bank = cache.synchronized {
      cache.getOrElseUpdate((seed, test.task, fewShotCount), generateBank(seed, test.task, fewShotCount))
    }
    val testShape = Generator.structureHash(test.family.program)
    val testNames = Generator.names(test.family.program)
    require(bank.forall(f => Generator.structureHash(f.program) != testShape), "Test duplicates a fixed example structure")
    require(bank.forall(f => Generator.names(f.program).intersect(testNames).isEmpty), "Test shares a block name with the fixed example bank")
    bank.map(f => ExpandedCase(f, test.task, test.view, test.spec))

  private def generateBank(seed: Long, task: Task, fewShotCount: Int): Vector[CaseFamily] =
    var shapes = Set.empty[String]
    var usedNames = Set.empty[String]
    Vector.tabulate(fewShotCount) { index =>
      var attempt = 0
      var selected: Option[CaseFamily] = None
      while selected.isEmpty && attempt < 10000 do
        val exampleSeed = Seeds.derive(seed, s"examples:${task.id}:$index", attempt)
        val generated = Generator.generateFamily(GenerationConfig(2 + index % 3, index % 9, index + attempt * 2), exampleSeed)
        val values = Generator.events(generated.program).collect { case ParsedEvent.Let(_, value) => value }.toSet
        val random = new Random(Seeds.derive(generated.valueSeed, "example-extra-binding", index))
        var extra = 1000 + random.nextInt(9000)
        while values.contains(extra) do extra = 1000 + random.nextInt(9000)
        // A non-target binding prevents name-only renamings from making tests copies of examples.
        val family = generated.copy(program = Program(Stmt.Let("x_b", extra) +: generated.program.body),
          before = generated.before.copy(eventCount = generated.before.eventCount + 1),
          after = generated.after.copy(eventCount = generated.after.eventCount + 1))
        val shape = Generator.structureHash(family.program)
        val names = Generator.names(family.program)
        if !shapes.contains(shape) && names.intersect(usedNames).isEmpty then
          selected = Some(family)
          shapes += shape
          usedNames ++= names
        attempt += 1
      val family = selected.getOrElse(throw new IllegalStateException("Cannot obtain independent few-shot structure"))
      family
    }
