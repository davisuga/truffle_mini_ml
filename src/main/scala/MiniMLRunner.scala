package simplelang

import org.graalvm.polyglot._

/**
 * Polyglot Runner for MiniML.
 * 
 * This class provides utilities for running MiniML code in a polyglot context,
 * enabling cross-language interoperability with Java, JavaScript, Python, etc.
 */
object MiniMLRunner {
  
  /**
   * Create a new polyglot context with MiniML enabled.
   * 
   * @param allowHostAccess If true, allows MiniML code to access Java classes
   * @return A configured polyglot Context
   */
  def createContext(allowHostAccess: Boolean = false): Context = {
    val builder = Context.newBuilder(MiniMLLanguage.ID)
      .allowExperimentalOptions(true)
    
    if (allowHostAccess) {
      builder
        .allowAllAccess(true)
        .allowHostAccess(HostAccess.ALL)
        .allowHostClassLookup(_ => true)
    }
    
    builder.build()
  }
  
  /**
   * Evaluate MiniML code and return the result.
   */
  def eval(code: String, allowHostAccess: Boolean = false): Value = {
    val context = createContext(allowHostAccess)
    try {
      context.eval(MiniMLLanguage.ID, code)
    } finally {
      context.close()
    }
  }
  
  /**
   * Evaluate MiniML code with pre-defined bindings.
   * 
   * @param code The MiniML source code
   * @param bindings Map of name -> value pairs to make available
   * @param allowHostAccess If true, allows Java class access
   */
  def evalWithBindings(
      code: String, 
      bindings: Map[String, Any],
      allowHostAccess: Boolean = false
  ): Value = {
    val context = createContext(allowHostAccess)
    try {
      // Add bindings to polyglot bindings
      val polyglotBindings = context.getPolyglotBindings
      bindings.foreach { case (name, value) =>
        polyglotBindings.putMember(name, value)
      }
      
      context.eval(MiniMLLanguage.ID, code)
    } finally {
      context.close()
    }
  }
  
  /**
   * Create a context, evaluate code, and extract a function that can be called from Java.
   * 
   * @param code MiniML code that should define a function
   * @param functionName The name of the function to extract
   * @return A Value representing the function
   */
  def getFunction(code: String, functionName: String): (Context, Value) = {
    val context = createContext()
    context.eval(MiniMLLanguage.ID, code)
    val function = context.getBindings(MiniMLLanguage.ID).getMember(functionName)
    (context, function)
  }
}

/**
 * Example runner demonstrating cross-language interop.
 */
@main def runMiniML() = {
  println("=== MiniML Polyglot Runner ===\n")
  
  // Example 1: Simple expression evaluation
  println("1. Simple expression evaluation:")
  val result1 = evaluateSimple("let x = 10 in let y = 20 in x + y")
  println(s"   let x = 10 in let y = 20 in x + y = $result1\n")
  
  // Example 2: Function definition and evaluation
  println("2. Function definition:")
  val result2 = evaluateSimple("let add x y = x + y in add 3 5")
  println(s"   let add x y = x + y in add 3 5 = $result2\n")
  
  // Example 3: Higher-order functions
  println("3. Higher-order functions:")
  val result3 = evaluateSimple(
    "let apply f x = f x in let double = (x) => x * 2 in apply double 21"
  )
  println(s"   apply double 21 = $result3\n")
  
  // Example 4: Conditional expressions
  println("4. Conditional expressions:")
  val result4 = evaluateSimple("if 5 > 3 then 100 else 200")
  println(s"   if 5 > 3 then 100 else 200 = $result4\n")
  
  // Example 5: Closures
  println("5. Closures:")
  val result5 = evaluateSimple(
    "let multiplier = 10 in let mult = (x) => x * multiplier in mult 5"
  )
  println(s"   Using closure with multiplier = $result5\n")
  
  println("=== Done ===")
}

/**
 * Helper to evaluate simple MiniML expressions without full polyglot context.
 */
def evaluateSimple(code: String): AnyRef = {
  import com.oracle.truffle.api.frame.FrameDescriptor
  
  val parseResult = Parser.parseInput(code)
  parseResult match {
    case fastparse.Parsed.Success(ast, _) =>
      val node = AstToTruffle.toTruffleNode(ast)
      val rootEnv = createRootEnvironment()
      val frame = com.oracle.truffle.api.Truffle.getRuntime.createVirtualFrame(
        Array[AnyRef](rootEnv), new FrameDescriptor()
      )
      node.execute(frame)
    case failure: fastparse.Parsed.Failure =>
      throw new RuntimeException(s"Parse error: ${failure.msg}")
  }
}
