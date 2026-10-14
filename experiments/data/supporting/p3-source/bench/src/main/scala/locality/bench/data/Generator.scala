package locality.bench.data

import locality.bench.lang.*
import java.util.Random

object Generator:
  val version = "generator-v1"
  def generateFamily(config: GenerationConfig, seed: Long): CaseFamily =
    require(config.depth >= 2 && config.depth <= 64, "depth must be 2..64")
    require(config.fillerStatements >= 0 && config.fillerStatements <= 100000, "invalid filler count")
    val ss = Seeds.derive(seed, "structure", config.index)
    val ids = Seeds.derive(seed, "identifier", config.index)
    val vs = Seeds.derive(seed, "value", config.index)
    val fs = Seeds.derive(seed, "filler", config.index)
    val sr = new Random(ss)
    val ir = new Random(ids)
    val vr = new Random(vs)
    val fr = new Random(fs)
    var used = Set.empty[String]
    def randomId(prefix: String, random: Random): String =
      var candidate = ""
      while candidate.isEmpty || used.contains(candidate) do candidate = prefix + (0 until 8).map(_ => ('a' + random.nextInt(26)).toChar).mkString
      used += candidate
      candidate
    val pathNames = Vector.fill(config.depth)(randomId("n_", ir))
    val siblings = Vector.fill(2)(randomId("n_", ir))
    val probes = Vector.fill(2)(randomId("q_", ir))
    val values = (1000 to 9999).toArray
    for i <- values.length - 1 to 1 by -1 do
      val j = vr.nextInt(i + 1); val t = values(i); values(i) = values(j); values(j) = t
    val kinds0 = Vector.fill(config.depth)(Kind.values(sr.nextInt(3)))
    val kinds = if kinds0.distinct.size == 1 then kinds0.updated(config.depth - 1, Kind.values((kinds0.last.ordinal + 1) % 3)) else kinds0
    val closes = math.max(1, math.min(config.depth - 1, (config.depth + 1) / 2))
    val remains = config.depth - closes
    val outerLookup = config.index % 2 != 0
    val ownerLevel = if outerLookup then remains - 1 else remains
    def sibling(index: Int): Stmt = Stmt.Scope(Kind.values(sr.nextInt(3)), siblings(index), Vector(Stmt.Let("x_a", values(config.depth + 1 + index)), Stmt.Nop(randomId("p_", fr))))
    val siblingStmts = Vector(sibling(0), sibling(1))
    // P3b library: a top-level block holding a decoy module (closed first, binds x_a to another value) and
    // max(1, depth/2) nested module levels whose innermost module binds x_a to the imported value.
    // Every draw below is skipped for import-free families, which therefore stay byte-identical.
    val libraryLevels = math.max(1, config.depth / 2)
    val libraryNames = if config.imports then Vector.fill(libraryLevels + 2)(randomId("n_", ir)) else Vector.empty
    val libraryKinds = if config.imports then Vector.fill(libraryLevels + 2)(Kind.values(sr.nextInt(3))) else Vector.empty
    val sourceModule = libraryNames.lastOption.getOrElse("")
    val library: Vector[Stmt] = if !config.imports then Vector.empty else
      def chain(i: Int): Stmt =
        val body = if i < libraryLevels + 1 then Vector(chain(i + 1))
          else Vector(Stmt.Let("x_a", values(config.depth + 3)), Stmt.Nop(randomId("p_", fr)))
        Stmt.Scope(libraryKinds(i), libraryNames(i), body)
      val decoy = Stmt.Scope(libraryKinds(1), libraryNames(1), Vector(Stmt.Let("x_a", values(config.depth + 4)), Stmt.Nop(randomId("p_", fr))))
      Vector(Stmt.Scope(libraryKinds(0), libraryNames(0), Vector(decoy, chain(2))))
    def path(level: Int): Stmt =
      val binding = if level == remains && outerLookup then Vector.empty
        else if config.imports && level == ownerLevel then Vector(Stmt.Import(sourceModule))
        else Vector(Stmt.Let("x_a", values(level)))
      val decoy = if level == math.max(1, remains - 1) then Vector(siblingStmts(1)) else Vector.empty
      val rest = if level < config.depth then Vector(path(level + 1)) else Vector.fill(config.fillerStatements)(Stmt.Nop(randomId("p_", fr)))
      Stmt.Scope(kinds(level - 1), pathNames(level - 1), binding ++ decoy ++ rest)
    // The scope that owns the visible binding after the closes imports it instead of binding it itself.
    val global: Vector[Stmt] = if config.imports && ownerLevel == 0 then Vector(siblingStmts(0)) ++ library :+ Stmt.Import(sourceModule)
      else Vector(Stmt.Let("x_a", values(0)), siblingStmts(0)) ++ library
    val program = Program(global :+ path(1))
    val es = events(program)
    val before = es.lastIndexWhere {
      case ParsedEvent.Let(_, _) | ParsedEvent.Nop(_) => true
      case _ => false
    } + 1
    val idText = s"$version:$seed:${config.depth}:${config.fillerStatements}:${config.index}" + (if config.imports then ":imports" else "")
    val family = CaseFamily(s"family-${Seeds.hash(idText).take(20)}", program, "x_a",
      CutPoint(before, probes(0), "x_a"), CutPoint(before + closes, probes(1), "x_a"), config.depth, config.fillerStatements, closes,
      ss, ids, vs, fs, siblings, if ownerLevel == 0 then None else Some(pathNames(ownerLevel - 1)), pathNames.last)
    family

  def expandFamily(family: CaseFamily, task: Task, view: Option[View], lexicon: Lexicon,
    styles: Vector[SyntaxStyle] = SyntaxStyle.values.toVector, qualification: Qualification = Qualification.Short): Vector[ExpandedCase] =
    require((task == Task.AstToSource) == view.isEmpty, "generation has no view; reading requires view")
    require(styles.nonEmpty && styles.distinct.size == styles.size, "styles must be non-empty and distinct")
    styles.map(s => ExpandedCase(family, task, view, RenderSpec(s, lexicon, qualification = qualification)))

  def nonceLexicon(seed: Long, indexWithinCell: Int): Lexicon =
    require(indexWithinCell >= 0)
    val order = (0 until 8).toArray
    val random = new Random(Seeds.derive(seed, "lexicon", 0))
    for i <- 7 to 1 by -1 do
      val j = random.nextInt(i + 1); val t = order(i); order(i) = order(j); order(j) = t
    Lexicon.fixedNonce(order(indexWithinCell % 8))

  def names(program: Program): Set[String] =
    def collect(body: Vector[Stmt]): Set[String] = body.flatMap {
      case Stmt.Scope(_, n, b) => collect(b) + n
      case _ => Set.empty[String]
    }.toSet
    collect(program.body)

  def structureHash(program: Program): String =
    def shape(body: Vector[Stmt]): String = body.map {
      case Stmt.Let(v, _) => s"L($v)"
      case Stmt.Nop(_) => "N"
      case Stmt.Import(_) => "I"
      case Stmt.Scope(k, _, b) => s"S(${k.natural},${shape(b)})"
    }.mkString("[", ",", "]")
    Seeds.hash(shape(program.body))

  def events(program: Program): Vector[ParsedEvent] =
    def walk(body: Vector[Stmt]): Vector[ParsedEvent] = body.flatMap {
      case Stmt.Let(v, n) => Vector(ParsedEvent.Let(v, n))
      case Stmt.Nop(p) => Vector(ParsedEvent.Nop(p))
      case Stmt.Import(t) => Vector(ParsedEvent.Import(t))
      case Stmt.Scope(k, n, b) => Vector(ParsedEvent.Open(k, n)) ++ walk(b) :+ ParsedEvent.Close
    }
    walk(program.body)

  lazy val smokeFamilies: Vector[CaseFamily] =
    def l(n: Int): Stmt = Stmt.Let("x_a", n)
    def s(k: Kind, name: String, body: Stmt*): Stmt = Stmt.Scope(k, name, body.toVector)
    val decoy1 = s(Kind.AreaScope, "n_siboneaa", l(7771), Stmt.Nop("p_siboneaa"))
    val decoy2 = s(Kind.FuncScope, "n_sibtwoaa", l(7772), Stmt.Nop("p_sibtwoaa"))
    val programs = Vector(
      Program(Vector(l(1048), decoy1, decoy2, s(Kind.UnitScope, "n_outeraaa", s(Kind.FuncScope, "n_inneraaa", l(5916))))),
      Program(Vector(l(2014), decoy1, s(Kind.UnitScope, "n_outeraaa", l(7321), decoy2, s(Kind.FuncScope, "n_inneraaa", l(5916))))),
      Program(Vector(l(3114), decoy1, s(Kind.UnitScope, "n_outeraaa", l(6332), decoy2, s(Kind.FuncScope, "n_middleaa", l(4221), s(Kind.AreaScope, "n_inneraaa", l(8604)))))),
      Program(Vector(l(9001), decoy1, s(Kind.FuncScope, "n_outeraaa", l(2123), decoy2, s(Kind.AreaScope, "n_middleaa", s(Kind.UnitScope, "n_deepaaaa", l(5621), s(Kind.FuncScope, "n_inneraaa", l(7824)))))))
    )
    programs.zipWithIndex.map { (program, i) =>
      val depth = Vector(2, 2, 3, 4)(i)
      val closes = Vector(1, 1, 2, 2)(i)
      val before = events(program).lastIndexWhere { case ParsedEvent.Let(_, _) => true; case _ => false } + 1
      CaseFamily(s"smoke-${i + 1}", program, "x_a", CutPoint(before, "q_beforeaa", "x_a"), CutPoint(before + closes, "q_afteraaa", "x_a"), depth, 0, closes,
        i.toLong, i.toLong, i.toLong, i.toLong, Vector("n_siboneaa", "n_sibtwoaa"), if i == 0 then None else Some("n_outeraaa"), "n_inneraaa")
    }
