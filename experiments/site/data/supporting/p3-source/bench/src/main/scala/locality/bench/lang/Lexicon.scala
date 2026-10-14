package locality.bench.lang

final case class Lexicon(unit: String, func: String, area: String, let: String, nop: String,
  end: String, importWord: String = "import", regime: String = "natural", setId: Int = -1):
  def keyword(kind: Kind): String = kind match
    case Kind.UnitScope => unit
    case Kind.FuncScope => func
    case Kind.AreaScope => area
  def kind(keyword: String): Option[Kind] = Kind.values.find(k => this.keyword(k) == keyword)

object Lexicon:
  val natural: Lexicon = Lexicon("unit", "func", "area", "let", "nop", "end", "import")
  // Fixed versioned banks, chosen without seeing any model response. The seventh word (the import
  // keyword) was added for P3b after the first six were fixed; it reuses no word from any set.
  val fixedNonce: Vector[Lexicon] = Vector(
    ("daxu", "mepo", "sovi", "kep", "rud", "bim", "tuv"),
    ("vomu", "zafi", "nelo", "dut", "hev", "pab", "gex"),
    ("kori", "fazu", "benu", "miv", "zup", "gol", "wam"),
    ("lavo", "pesi", "tumu", "reb", "sok", "vix", "jen"),
    ("feni", "havu", "zomu", "dal", "nup", "tek", "cob"),
    ("raku", "weso", "bifo", "zim", "vop", "hud", "pyl"),
    ("nezu", "sapu", "jomi", "fal", "kiv", "wor", "duz"),
    ("xaru", "telo", "gupi", "sem", "bov", "nix", "quf")
  ).zipWithIndex.map { case ((u, f, a, l, n, e, i), index) => Lexicon(u, f, a, l, n, e, i, "nonce", index) }
