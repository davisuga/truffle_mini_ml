package simplelang

val src = """
// true
// false
let add x y = 
  let a = 1
  let b = 2
  a + b
// a + b
"""

import simplelang.Ast
import simplelang.Ast.Expr

object JsGenerator:
  def generate(expr: Expr): String = expr match
    case Ast.Expr.IntLit(value)  => value.toString
    case Ast.Expr.StrLit(value)  => s""""$value""""
    case Ast.Expr.BoolLit(value) => value.toString
    case Ast.Expr.If(cond, thenBranch, elseBranch) =>
      s"if (${generate(cond)}) { ${generate(thenBranch)} } else { ${generate(elseBranch)} }"
    case Ast.Expr.Fn(params, body) =>
      s"(${params.mkString(", ")}) => { return ${generate(body)}; }"
    case Ast.Expr.Call(fn, args) =>
      s"${generate(fn)}(${args.map(generate).mkString(", ")})"
    case Ast.Expr.Let(name, value, body) =>
      s"let $name = ${generate(value)}; ${generate(body)}"
    case Ast.Expr.Ident(name)     => name
    case Ast.Expr.ExprList(exprs) => exprs.map(generate).mkString("; ")

import simplelang.Parser.parseInput
@main def hello =
  val lines = scala.io.Source.fromFile("ex.miniml").mkString
  val ast = (parseInput(src))
  println(ast)
  print(JsGenerator.generate(ast.get.value))
