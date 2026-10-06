package locality.bench.lang

final case class Lexicon(unit: String, func: String, area: String, let: String, nop: String,
  end: String, regime: String = "natural", setId: Int = -1):
  def keyword(kind: Kind): String = kind match
    case Kind.UnitScope => unit
    case Kind.FuncScope => func
    case Kind.AreaScope => area
  def kind(keyword: String): Option[Kind] = Kind.values.find(k => this.keyword(k) == keyword)

object Lexicon:
  val natural: Lexicon = Lexicon("unit", "func", "area", "let", "nop", "end")
  // Fixed versioned banks, chosen without seeing any model response.
  val fixedNonce: Vector[Lexicon] = Vector(
    ("daxu", "mepo", "sovi", "kep", "rud", "bim"),
    ("vomu", "zafi", "nelo", "dut", "hev", "pab"),
    ("kori", "fazu", "benu", "miv", "zup", "gol"),
    ("lavo", "pesi", "tumu", "reb", "sok", "vix"),
    ("feni", "havu", "zomu", "dal", "nup", "tek"),
    ("raku", "weso", "bifo", "zim", "vop", "hud"),
    ("nezu", "sapu", "jomi", "fal", "kiv", "wor"),
    ("xaru", "telo", "gupi", "sem", "bov", "nix")
  ).zipWithIndex.map { case ((u, f, a, l, n, e), i) => Lexicon(u, f, a, l, n, e, "nonce", i) }
