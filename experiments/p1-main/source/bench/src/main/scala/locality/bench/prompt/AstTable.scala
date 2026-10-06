package locality.bench.prompt

import locality.bench.lang.*
import java.util.Random
import scala.util.boundary
import boundary.break

object AstTable:
  private val header = "node\tparent\torder\ttag\tkind\tname\tvariable\tvalue\tpayload"
  private final case class Row(id: String, parent: String, order: Int, tag: String, kind: String = "-",
    name: String = "-", variable: String = "-", value: String = "-", payload: String = "-"):
    def text: String = Vector(id, parent, order.toString, tag, kind, name, variable, value, payload).mkString("\t")

  def render(program: Program, seed: Long): String =
    val random = new Random(seed)
    var used = Set.empty[String]
    def id(): String =
      var name = ""
      while name.isEmpty || used.contains(name) do name = "d_" + (0 until 12).map(_ => ('a' + random.nextInt(26)).toChar).mkString
      used += name
      name
    val root = id()
    var rows = Vector(Row(root, "-", 0, "ROOT"))
    def visit(body: Vector[Stmt], parent: String): Unit = body.zipWithIndex.foreach { (stmt, order) =>
      val node = id()
      stmt match
        case Stmt.Let(v, n) => rows :+= Row(node, parent, order, "LET", variable = v, value = n.toString)
        case Stmt.Nop(p) => rows :+= Row(node, parent, order, "NOP", payload = p)
        case Stmt.Scope(k, n, b) =>
          rows :+= Row(node, parent, order, "SCOPE", kind = k.natural, name = n)
          visit(b, node)
    }
    visit(program.body, root)
    var remaining = rows.tail
    var shown = Set(root)
    val ordered = Vector.newBuilder[Row]
    ordered += rows.head
    while remaining.nonEmpty do
      val ready = remaining.filter(r => shown.contains(r.parent))
      val next = ready(random.nextInt(ready.size))
      ordered += next
      shown += next.id
      remaining = remaining.filterNot(_.id == next.id)
    header + "\n" + ordered.result().map(_.text).mkString("", "\n", "\n")

  def parse(table: String): Either[String, Program] = boundary:
    val lines = table.linesIterator.map(_.trim).filter(_.nonEmpty).toVector
    if lines.isEmpty || lines.head.split("[ \\t]+").toVector != header.split("\t").toVector then break(Left("invalid table header"))
    var rows = Vector.empty[Row]
    var known = Map.empty[String, Row]
    var names = Set.empty[String]
    for line <- lines.tail do
      val w = line.split("[ \\t]+").toVector
      if w.size != 9 || !w(0).matches("[a-zA-Z0-9_]{1,64}") || !w(2).matches("0|[1-9][0-9]{0,6}") then break(Left("invalid table row"))
      if known.contains(w(0)) then break(Left("duplicate node ID"))
      val row = Row(w(0), w(1), w(2).toInt, w(3), w(4), w(5), w(6), w(7), w(8))
      if rows.isEmpty then
        if row.tag != "ROOT" || row.parent != "-" || row.order != 0 || w.drop(4).exists(_ != "-") then break(Left("invalid root"))
      else
        if !known.contains(row.parent) || !Set("ROOT", "SCOPE").contains(known(row.parent).tag) then break(Left("parent must precede child and contain a body"))
        row.tag match
          case "LET" =>
            if !row.variable.matches("x_[a-f]") || !row.value.matches("[1-9][0-9]{3}") || row.kind != "-" || row.name != "-" || row.payload != "-" then break(Left("invalid LET"))
          case "NOP" =>
            if !row.payload.matches("p_[a-z]{8}") || Vector(row.kind, row.name, row.variable, row.value).exists(_ != "-") then break(Left("invalid NOP"))
          case "SCOPE" =>
            if !Kind.values.exists(_.natural == row.kind) || !row.name.matches("n_[a-z]{8}") || Vector(row.variable, row.value, row.payload).exists(_ != "-") then break(Left("invalid SCOPE"))
            if names.contains(row.name) then break(Left("duplicate block name"))
            names += row.name
          case _ => break(Left("unsupported table tag"))
      rows :+= row
      known += row.id -> row
    if rows.isEmpty then break(Left("missing root"))
    val children = rows.tail.groupBy(_.parent).view.mapValues(_.sortBy(_.order)).toMap
    for (_, group) <- children do
      if group.map(_.order) != group.indices.toVector then break(Left("noncontiguous or duplicate sibling order"))
      val bindings = group.filter(_.tag == "LET").map(_.variable)
      if bindings.distinct.size != bindings.size then break(Left("duplicate same-scope binding"))
    def body(parent: String): Vector[Stmt] = children.getOrElse(parent, Vector.empty).map { row =>
      row.tag match
        case "LET" => Stmt.Let(row.variable, row.value.toInt)
        case "NOP" => Stmt.Nop(row.payload)
        case "SCOPE" => Stmt.Scope(Kind.values.find(_.natural == row.kind).get, row.name, body(row.id))
        case _ => throw new IllegalStateException("unreachable table tag")
    }
    Right(Program(body(rows.head.id)))
