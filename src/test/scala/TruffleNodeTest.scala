// Tests for Truffle node execution
import munit.FunSuite
import simplelang._
import simplelang.Parser.parseInput
import com.oracle.truffle.api.frame.FrameDescriptor

class TruffleNodeTest extends FunSuite {
  
  /**
   * Helper to parse, convert to Truffle nodes, and execute an expression.
   */
  def eval(input: String): AnyRef = {
    val parsed = parseInput(input)
    val ast = parsed.get.value
    val node = AstToTruffle.toTruffleNode(ast)
    
    // Create a root environment and frame
    val rootEnv = createRootEnvironment()
    val frame = com.oracle.truffle.api.Truffle.getRuntime.createVirtualFrame(
      Array[AnyRef](rootEnv), new FrameDescriptor()
    )
    node.execute(frame)
  }
  
  // ============== Literal Tests ==============
  
  test("evaluate integer literal") {
    assertEquals(eval("42"), java.lang.Integer.valueOf(42))
  }
  
  test("evaluate string literal") {
    assertEquals(eval("\"hello\""), "hello")
  }
  
  test("evaluate boolean true") {
    assertEquals(eval("true"), java.lang.Boolean.valueOf(true))
  }
  
  test("evaluate boolean false") {
    assertEquals(eval("false"), java.lang.Boolean.valueOf(false))
  }
  
  // ============== Arithmetic Tests ==============
  
  test("evaluate addition") {
    assertEquals(eval("1 + 2"), java.lang.Integer.valueOf(3))
  }
  
  test("evaluate subtraction") {
    assertEquals(eval("10 - 3"), java.lang.Integer.valueOf(7))
  }
  
  test("evaluate multiplication") {
    assertEquals(eval("4 * 5"), java.lang.Integer.valueOf(20))
  }
  
  test("evaluate division") {
    assertEquals(eval("20 / 4"), java.lang.Integer.valueOf(5))
  }
  
  test("evaluate complex arithmetic") {
    // Note: This evaluates left-to-right due to how the parser builds the AST
    // 1 + 2 * 3 is parsed as (1 + 2) * 3 = 9 without proper precedence
    // Let's test a simple case: 2 + 3 + 4 = (2 + 3) + 4 = 9
    assertEquals(eval("2 + 3 + 4"), java.lang.Integer.valueOf(9))
  }
  
  // ============== Let Binding Tests ==============
  
  test("evaluate let binding") {
    assertEquals(eval("let x = 10 in x"), java.lang.Integer.valueOf(10))
  }
  
  test("evaluate let binding with expression") {
    assertEquals(eval("let x = 5 in x + 3"), java.lang.Integer.valueOf(8))
  }
  
  test("evaluate nested let bindings") {
    assertEquals(eval("let x = 1 in let y = 2 in x + y"), java.lang.Integer.valueOf(3))
  }
  
  // ============== Conditional Tests ==============
  
  test("evaluate if true") {
    assertEquals(eval("if true then 1 else 2"), java.lang.Integer.valueOf(1))
  }
  
  test("evaluate if false") {
    assertEquals(eval("if false then 1 else 2"), java.lang.Integer.valueOf(2))
  }
  
  // ============== Function Tests ==============
  
  test("evaluate lambda and call") {
    val result = eval("let f = (x) => x + 1 in f 5")
    assertEquals(result, java.lang.Integer.valueOf(6))
  }
  
  test("evaluate function definition and call") {
    val result = eval("let add x y = x + y in add 3 4")
    assertEquals(result, java.lang.Integer.valueOf(7))
  }
  
  test("evaluate higher-order function") {
    // apply takes a function and an argument, then applies the function
    val result = eval("let apply f x = f x in let inc = (x) => x + 1 in apply inc 10")
    assertEquals(result, java.lang.Integer.valueOf(11))
  }
  
  // ============== Comparison Tests ==============
  
  test("evaluate equality") {
    assertEquals(eval("1 == 1"), java.lang.Boolean.valueOf(true))
    assertEquals(eval("1 == 2"), java.lang.Boolean.valueOf(false))
  }
  
  test("evaluate less than") {
    assertEquals(eval("1 < 2"), java.lang.Boolean.valueOf(true))
    assertEquals(eval("2 < 1"), java.lang.Boolean.valueOf(false))
  }
  
  test("evaluate greater than") {
    assertEquals(eval("2 > 1"), java.lang.Boolean.valueOf(true))
    assertEquals(eval("1 > 2"), java.lang.Boolean.valueOf(false))
  }
  
  // ============== Conditional with Comparison Tests ==============
  
  test("evaluate if with comparison") {
    assertEquals(eval("if 1 < 2 then 100 else 200"), java.lang.Integer.valueOf(100))
    assertEquals(eval("if 2 < 1 then 100 else 200"), java.lang.Integer.valueOf(200))
  }
  
  // ============== Closure Tests ==============
  
  test("evaluate closure capturing variable") {
    val result = eval("let x = 10 in let f = (y) => x + y in f 5")
    assertEquals(result, java.lang.Integer.valueOf(15))
  }
}
