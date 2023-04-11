package simplelang
import fastparse._
import simplelang.Ast
import simplelang.Ast.Expr
import simplelang.Ast.Expr.*
import sourcecode.Text.generate

object Parser:
  def parseInput(input: String): Parsed[Expr] = parse(input, expr(_))

  import fastparse.SingleLineWhitespace.whitespace

  def ws[$: P]: P[Unit] = P(CharsWhileIn(" \t\r\n").?)

  def nl[$: P]: P[Unit] = P("\r\n" | "\n")

  def intLit[$: P]: P[IntLit] = P(
    CharIn("0-9").rep(1).!.map(_.toInt).map(IntLit.apply)
  )

  def strLit[$: P]: P[StrLit] =
    P("\"" ~ CharsWhile(_ != '"', min = 0).! ~ "\"").map(StrLit.apply)

  def expr[$: P]: P[Expr] = P(
    conditional | let | lambda | call | literal | identifier
  )

  def literal[$: P]: P[Expr] = P(intLit | strLit | boolLit)

  def boolLit[$: P]: P[BoolLit] =
    P(("true" | "false").!.map(_.toBoolean)).map(BoolLit.apply)

  def identifier[$: P]: P[Ident] =
    P(CharIn("a-zA-Z") ~ CharIn("a-zA-Z0-9").rep).!.map(Ident.apply)

  def lambda[$: P]: P[Fn] =
    P("(" ~ paramList ~ ")" ~ "=>" ~ expr).map((pList, e) =>
      Fn(pList.map(_.name), e)
    )

  def parens[$: P]: P[Expr] = P("(" ~/ expr ~ ")")

  def paramList[$: P]: P[List[Ident]] = P(
    identifier.rep(sep = ",").map(_.toList)
  )

  def conditional[$: P]: P[If] =
    P("if" ~ expr ~ "then" ~ expr ~ "else" ~ expr).map(If(_, _, _))

  def call[$: P]: P[Call] =
    P(identifier ~ "(" ~ exprList ~ ")").map(Call(_, _))

  def exprList[$: P]: P[List[Expr]] = P(expr.rep(sep = ",").map(_.toList))

  def let[$: P]: P[Let] = P(
    "let" ~ identifier ~ "=" ~ expr ~ (nl.rep(1) ~ "in").? ~ nl.rep ~ expr
  ).map(Let.fromProduct)
