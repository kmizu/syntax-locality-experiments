package locality.bench.data

import locality.bench.lang.*

enum View(val id: String):
  case BeforeClose extends View("before_close")
  case AfterClose extends View("after_close")
object View:
  def fromId(id: String): Option[View] = values.find(_.id == id)

enum Task(val id: String):
  case ScopeLookup extends Task("scope_lookup")
  case ActiveStack extends Task("active_stack")
  case AstToSource extends Task("ast_to_source")
object Task:
  def fromId(id: String): Option[Task] = values.find(_.id == id)

final case class GenerationConfig(depth: Int, fillerStatements: Int, index: Int = 0)
final case class CaseFamily(id: String, program: Program, targetVariable: String, before: CutPoint,
  after: CutPoint, peakDepth: Int, fillerStatements: Int, closeCount: Int,
  structureSeed: Long, identifierSeed: Long, valueSeed: Long, fillerSeed: Long,
  siblingNames: Vector[String], visibleOwner: Option[String], focusedName: String):
  def cut(view: View): CutPoint = if view == View.BeforeClose then before else after

final case class ExpandedCase(family: CaseFamily, task: Task, view: Option[View], spec: RenderSpec):
  def id: String = s"${family.id}:${task.id}:${view.map(_.id).getOrElse("complete")}:${spec.lexicon.regime}:${spec.lexicon.setId}:${spec.style.id}"
  def goldObservation: Observation = ReferenceOracle.observe(family.program, family.cut(view.getOrElse(View.AfterClose))).fold(e => throw new IllegalArgumentException(e.message), identity)
  def goldSource: String = Renderer.renderProgram(family.program, spec)
  def source: String = if task == Task.AstToSource then goldSource else Renderer.renderPrefix(family.program, family.cut(view.get), spec)
