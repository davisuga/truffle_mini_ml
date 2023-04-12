// For more information on writing tests, see
// https://scalameta.org/munit/docs/getting-started.html
import munit.FunSuite
import simplelang.Ast.Expr
import simplelang.Ast.Expr.*
import simplelang.Parser.parseInput

class ParserTest extends FunSuite {
  // Assuming you have a `parse` function that takes a string and returns an Expr
  // def parse(input: String): Expr[_] = ???

  test("parse integer literal") {
    val input = "42"
    val expected = IntLit(42)
    assertEquals(parseInput(input).get.value, expected)
  }

  test("parse string literal") {
    val input = "\"hello\""
    val expected = StrLit("hello")
    assertEquals(parseInput(input).get.value, expected)
  }

  test("parse boolean literal") {
    val input = "true"
    val expected = BoolLit(true)
    assertEquals(parseInput(input).get.value, expected)
  }

  test("parse let expression") {
    val input = "let x = 1 in x"
    val expected = Let("x", IntLit(1), Ident("x"))
    assertEquals(parseInput(input).get.value, expected)
  }
  test("parse function expression") {
    val input = "(x) => x"
    val expected = Fn(List("x"), Ident("x"))
    assertEquals(parseInput(input).get.value, expected)
  }

  test("parse function definition and call") {
    val input = "let add x y = x + y in add 1 2"
    val expected = Let(
      "add",
      Fn(List("x", "y"), Call(Ident("+"), List(Ident("x"), Ident("y")))),
      Call((Ident("add"): Expr), List(IntLit(1), IntLit(2)))
    )
    assertEquals(parseInput(input).get.value, expected)
  }
}
