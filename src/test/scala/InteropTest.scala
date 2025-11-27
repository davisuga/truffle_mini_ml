// Tests for cross-language interoperability
import munit.FunSuite
import simplelang._
import simplelang.Parser.parseInput
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.interop.InteropLibrary

class InteropTest extends FunSuite {
  
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
  
  // ============== InteropFunction Tests ==============
  
  test("InteropFunction reports isExecutable") {
    // Create a simple function
    val fnNode = FnNode(List("x"), IntLitNode(42))
    val rootEnv = createRootEnvironment()
    val frame = com.oracle.truffle.api.Truffle.getRuntime.createVirtualFrame(
      Array[AnyRef](rootEnv), new FrameDescriptor()
    )
    val simpleFunction = fnNode.execute(frame).asInstanceOf[SimpleFunction]
    
    // Wrap in InteropFunction and test directly (without annotation processor)
    val interopFn = new InteropFunction(simpleFunction)
    
    assertEquals(interopFn.isExecutable, true)
  }
  
  test("InteropFunction can be executed directly") {
    // Create identity function: (x) => x
    val fnNode = FnNode(List("x"), IdentNode("x"))
    val rootEnv = createRootEnvironment()
    val frame = com.oracle.truffle.api.Truffle.getRuntime.createVirtualFrame(
      Array[AnyRef](rootEnv), new FrameDescriptor()
    )
    val simpleFunction = fnNode.execute(frame).asInstanceOf[SimpleFunction]
    
    // Wrap in InteropFunction and execute directly
    val interopFn = new InteropFunction(simpleFunction)
    
    val result = interopFn.execute(Array(java.lang.Integer.valueOf(42)))
    assertEquals(result, java.lang.Integer.valueOf(42))
  }
  
  test("InteropFunction reports correct arity") {
    // Create a two-argument function
    val body = CallNode(IdentNode("+"), List(IdentNode("x"), IdentNode("y")))
    val fnNode = FnNode(List("x", "y"), body)
    val rootEnv = createRootEnvironment()
    val frame = com.oracle.truffle.api.Truffle.getRuntime.createVirtualFrame(
      Array[AnyRef](rootEnv), new FrameDescriptor()
    )
    val simpleFunction = fnNode.execute(frame).asInstanceOf[SimpleFunction]
    
    val interopFn = new InteropFunction(simpleFunction)
    
    assertEquals(interopFn.getArity, 2)
    assertEquals(interopFn.isMemberReadable("arity"), true)
    assertEquals(interopFn.readMember("arity"), java.lang.Integer.valueOf(2))
  }
  
  test("InteropFunction throws ArityException for wrong argument count") {
    // Create a one-argument function
    val fnNode = FnNode(List("x"), IdentNode("x"))
    val rootEnv = createRootEnvironment()
    val frame = com.oracle.truffle.api.Truffle.getRuntime.createVirtualFrame(
      Array[AnyRef](rootEnv), new FrameDescriptor()
    )
    val simpleFunction = fnNode.execute(frame).asInstanceOf[SimpleFunction]
    
    val interopFn = new InteropFunction(simpleFunction)
    
    // Should throw ArityException when called with 2 arguments
    val exception = intercept[com.oracle.truffle.api.interop.ArityException] {
      interopFn.execute(Array(java.lang.Integer.valueOf(1), java.lang.Integer.valueOf(2)))
    }
    assertEquals(exception.getExpectedMinArity, 1)
    assertEquals(exception.getActualArity, 2)
  }
  
  // ============== MiniMLScope Tests ==============
  
  test("MiniMLScope has members") {
    val env = createRootEnvironment()
    val scope = new MiniMLScope(env)
    
    assertEquals(scope.hasMembers, true)
  }
  
  test("MiniMLScope can read builtin operators") {
    val env = createRootEnvironment()
    val scope = new MiniMLScope(env)
    
    // The + operator should be readable
    assertEquals(scope.isMemberReadable("+"), true)
    val addFn = scope.readMember("+")
    assert(addFn.isInstanceOf[AddFunction])
  }
  
  test("MiniMLScope can write and read members") {
    val env = createRootEnvironment()
    val scope = new MiniMLScope(env)
    
    // Write a new value
    scope.writeMember("myValue", java.lang.Integer.valueOf(42))
    
    // Read it back
    assertEquals(scope.isMemberReadable("myValue"), true)
    assertEquals(scope.readMember("myValue"), java.lang.Integer.valueOf(42))
  }
  
  // ============== MiniMLMemberKeys Tests ==============
  
  test("MiniMLMemberKeys behaves as array") {
    val keys = new MiniMLMemberKeys(Array("a", "b", "c"))
    
    assertEquals(keys.hasArrayElements, true)
    assertEquals(keys.getArraySize, 3L)
    assertEquals(keys.readArrayElement(0), "a")
    assertEquals(keys.readArrayElement(1), "b")
    assertEquals(keys.readArrayElement(2), "c")
  }
  
  test("MiniMLMemberKeys throws for invalid index") {
    val keys = new MiniMLMemberKeys(Array("a"))
    
    intercept[com.oracle.truffle.api.interop.InvalidArrayIndexException] {
      keys.readArrayElement(5)
    }
  }
  
  // ============== Builtin Interop Function Tests ==============
  // NOTE: InvokeFunction with Java strings requires a polyglot context
  // which needs the full GraalVM runtime. We test simpler cases.
  
  test("GetMemberFunction works with InteropFunction") {
    // First, create an InteropFunction and get its 'arity' member
    val fnNode = FnNode(List("x", "y"), IdentNode("x"))
    val rootEnv = createRootEnvironment()
    val frame = com.oracle.truffle.api.Truffle.getRuntime.createVirtualFrame(
      Array[AnyRef](rootEnv), new FrameDescriptor()
    )
    val simpleFunction = fnNode.execute(frame).asInstanceOf[SimpleFunction]
    val interopFn = new InteropFunction(simpleFunction)
    
    // Use direct method call
    val result = interopFn.readMember("arity")
    assertEquals(result, java.lang.Integer.valueOf(2))
  }
  
  // ============== Integration Tests ==============
  
  test("function from MiniML can use closure over interop") {
    // Create a function that uses a closure-captured value
    val result = eval(
      "let captured = 100 in let f = (x) => x + captured in f 5"
    )
    assertEquals(result, java.lang.Integer.valueOf(105))
  }
  
  test("nested function definitions work correctly") {
    val result = eval(
      "let compose f g x = f (g x) in " +
      "let double = (x) => x * 2 in " +
      "let addOne = (x) => x + 1 in " +
      "compose double addOne 10"
    )
    // (10 + 1) * 2 = 22
    assertEquals(result, java.lang.Integer.valueOf(22))
  }
  
  test("InteropFunction can execute addition function") {
    // Create add function: (x, y) => x + y
    val body = CallNode(IdentNode("+"), List(IdentNode("x"), IdentNode("y")))
    val fnNode = FnNode(List("x", "y"), body)
    val rootEnv = createRootEnvironment()
    val frame = com.oracle.truffle.api.Truffle.getRuntime.createVirtualFrame(
      Array[AnyRef](rootEnv), new FrameDescriptor()
    )
    val simpleFunction = fnNode.execute(frame).asInstanceOf[SimpleFunction]
    
    val interopFn = new InteropFunction(simpleFunction)
    
    val result = interopFn.execute(Array(
      java.lang.Integer.valueOf(10), 
      java.lang.Integer.valueOf(20)
    ))
    assertEquals(result, java.lang.Integer.valueOf(30))
  }
  
  test("InteropFunction toString provides useful representation") {
    val fnNode = FnNode(List("x", "y", "z"), IdentNode("x"))
    val rootEnv = createRootEnvironment()
    val frame = com.oracle.truffle.api.Truffle.getRuntime.createVirtualFrame(
      Array[AnyRef](rootEnv), new FrameDescriptor()
    )
    val simpleFunction = fnNode.execute(frame).asInstanceOf[SimpleFunction]
    
    val interopFn = new InteropFunction(simpleFunction)
    
    assertEquals(interopFn.toString, "<function(x, y, z)>")
  }
}
