package simplelang
import fastparse._
import simplelang.Ast
import simplelang.Ast.Expr
import simplelang.Ast.Expr.*
import sourcecode.Text.generate

val keywordList = Set(
  "else",
  "if",
  "import",
  "in",
  "let",
  ""
)
object Parser:

  def parseInput(input: String): Parsed[Expr] =
    parse(input, topLevelExpr(_)).value

  def ws[$: P]: P[Unit] = P(CharsWhileIn(" \t\r\n").?)
  def nl[$: P]: P[Unit] = P("\r\n" | "\n")
  def letter[$: P] = P(lowercase | uppercase)
  def lowercase[$: P] = P(CharIn("a-z"))
  def uppercase[$: P] = P(CharIn("A-Z"))
  def digit[$: P] = P(CharIn("0-9"))

  import fastparse.ScalaWhitespace.whitespace

  def primaryExpr[$: P]: P[Expr] = P(
    parens | let | conditional | lambda | call | literal | identifier
  )

  def opExpr[$: P]: P[Expr] = P(
    primaryExpr ~ (ws ~ binOp ~ ws ~ primaryExpr).rep
  ).map { case (init, ops) =>
    ops.foldLeft(init) { case (acc, (op, rhs)) =>
      Call(Ident(op), List(acc, rhs))
    }
  }

  def expr[$: P]: P[Expr] = P(ws ~ (opExpr | primaryExpr) ~ ws)

  def binOp[$: P]: P[String] = P(CharsWhileIn("=!@%^&*+<>|/", min = 1).!)

  def identifier[$: P]: P[Ident] =
    import fastparse.NoWhitespace.noWhitespaceImplicit
    P((letter | "_") ~ (letter | digit | "_").rep).!.filter(
      !keywordList.contains(_)
    ).map(Ident.apply)

  def intLit[$: P]: P[IntLit] = P(
    CharIn("0-9").rep(1).!.map(_.toInt).map(IntLit.apply)
  )

  def strLit[$: P]: P[StrLit] =
    P("\"" ~ CharsWhile(_ != '"', min = 0).! ~ "\"").map(StrLit.apply)

  def literal[$: P]: P[Expr] = P(intLit | strLit | boolLit)

  def boolLit[$: P]: P[BoolLit] =
    P(("true" | "false").!.map(_.toBoolean)).map(BoolLit.apply)

  def lambda[$: P]: P[Fn] =
    P("(" ~ paramList ~ ")" ~ "=>" ~ expr).map((pList, e) =>
      Fn(pList.map(_.name), e)
    )

  def parens[$: P]: P[Expr] = P("(" ~ expr ~ ")")

  def paramList[$: P]: P[List[Ident]] = P(
    identifier.rep(1, sep = ",").map(_.toList)
  )

  def conditional[$: P]: P[If] =
    P("if" ~ expr ~ "then" ~ expr ~ "else" ~ expr).map(If(_, _, _))

  def call[$: P]: P[Call] =
    P(identifier ~ (ws ~ identifier).rep(min = 1)).map((fnName, args) =>
      Call((fnName), args.toList)
    )

  def parameters[$: P]: P[List[Expr]] = P(expr.rep(sep = ",").map(_.toList))

  def exprList[$: P]: P[ExprList] = P(
    expr.rep(min = 1, sep = nl).map(exprs => ExprList(exprs.toList))
  )

  def let[$: P]: P[Let] = P(
    "let " ~ identifier.rep(
      min = 1,
      sep = Pass
    ) ~ "=" ~ expr ~ (ws ~ ("in" | nl).? ~ ws ~ expr).?
  ).map((identifiers, exp, bodyOpt) =>
    val idList = identifiers.toList.map(_.name)
    val body = bodyOpt.getOrElse(ExprList(List()))
    Let(
      idList(0),
      if idList.length > 1 then Fn(idList.tail, exp) else exp,
      body
    )
  )

  def topLevelExpr[$: P]: P[Expr] = P(ws.? ~ (exprList | expr) ~ ws.?)
