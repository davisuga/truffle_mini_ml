package simplelang

import com.oracle.truffle.api.frame.{FrameDescriptor, VirtualFrame}
import com.oracle.truffle.api.nodes.Node

import scala.collection.mutable

/**
 * Base trait for all SimpleLang AST nodes.
 * Following the Truffle manual pattern for manual node implementation.
 */
trait SimpleLangNode {
  def execute(frame: VirtualFrame): AnyRef
  
  /**
   * Execute and return an Int, throwing if the result is not an Int.
   */
  def executeInt(frame: VirtualFrame): Int = {
    execute(frame) match {
      case i: java.lang.Integer => i.intValue()
      case other => throw new RuntimeException(s"Expected Int, got ${other.getClass.getSimpleName}")
    }
  }
  
  /**
   * Execute and return a Boolean, throwing if the result is not a Boolean.
   */
  def executeBool(frame: VirtualFrame): Boolean = {
    execute(frame) match {
      case b: java.lang.Boolean => b.booleanValue()
      case other => throw new RuntimeException(s"Expected Boolean, got ${other.getClass.getSimpleName}")
    }
  }
}

// ============== Literal Nodes ==============

case class IntLitNode(value: Int) extends SimpleLangNode {
  override def execute(frame: VirtualFrame): AnyRef = 
    java.lang.Integer.valueOf(value)
}

case class StrLitNode(value: String) extends SimpleLangNode {
  override def execute(frame: VirtualFrame): AnyRef = value
}

case class BoolLitNode(value: Boolean) extends SimpleLangNode {
  override def execute(frame: VirtualFrame): AnyRef = 
    java.lang.Boolean.valueOf(value)
}

// ============== Control Flow Nodes ==============

case class IfNode(
    cond: SimpleLangNode,
    thenBranch: SimpleLangNode,
    elseBranch: SimpleLangNode
) extends SimpleLangNode {
  override def execute(frame: VirtualFrame): AnyRef = {
    val condValue = cond.executeBool(frame)
    if (condValue) thenBranch.execute(frame) else elseBranch.execute(frame)
  }
}

// ============== Environment for Variable Bindings ==============

/**
 * A simple environment for variable bindings.
 * Uses a chain of scopes for lexical scoping.
 */
class Environment(val parent: Option[Environment] = None) {
  private val bindings = mutable.Map[String, AnyRef]()
  
  def define(name: String, value: AnyRef): Unit = {
    bindings(name) = value
  }
  
  def lookup(name: String): AnyRef = {
    bindings.get(name) match {
      case Some(value) => value
      case None => parent match {
        case Some(p) => p.lookup(name)
        case None => throw new RuntimeException(s"Undefined variable: $name")
      }
    }
  }
  
  def extend(): Environment = new Environment(Some(this))
}

/**
 * Key for storing the environment in the frame's arguments.
 */
object FrameKeys {
  val ENV_INDEX = 0
}

// ============== Let Binding Nodes ==============

case class LetNode(
    name: String, 
    value: SimpleLangNode, 
    body: SimpleLangNode
) extends SimpleLangNode {
  override def execute(frame: VirtualFrame): AnyRef = {
    val env = getEnvironment(frame)
    val newEnv = env.extend()
    val evaluatedValue = value.execute(frame)
    newEnv.define(name, evaluatedValue)
    
    // Create a new frame with the extended environment
    val newArgs = Array[AnyRef](newEnv)
    val newFrame = com.oracle.truffle.api.Truffle.getRuntime.createVirtualFrame(
      newArgs, frame.getFrameDescriptor
    )
    body.execute(newFrame)
  }
}

// ============== Identifier Nodes ==============

case class IdentNode(name: String) extends SimpleLangNode {
  override def execute(frame: VirtualFrame): AnyRef = {
    val env = getEnvironment(frame)
    env.lookup(name)
  }
}

// ============== Function Nodes ==============

/**
 * A function value that captures its lexical environment.
 */
class SimpleFunction(
    val params: List[String],
    val body: SimpleLangNode,
    val closureEnv: Environment
) {
  override def toString: String = s"<function(${params.mkString(", ")})>"
}

case class FnNode(params: List[String], body: SimpleLangNode) extends SimpleLangNode {
  override def execute(frame: VirtualFrame): AnyRef = {
    val env = getEnvironment(frame)
    new SimpleFunction(params, body, env)
  }
}

// ============== Call Nodes ==============

case class CallNode(fn: SimpleLangNode, args: List[SimpleLangNode]) extends SimpleLangNode {
  override def execute(frame: VirtualFrame): AnyRef = {
    val fnValue = fn.execute(frame)
    fnValue match {
      case function: SimpleFunction =>
        // Evaluate arguments in the current frame
        val evaluatedArgs = args.map(_.execute(frame))
        
        // Create new environment extending the closure's environment
        val callEnv = function.closureEnv.extend()
        
        // Bind parameters to arguments
        function.params.zip(evaluatedArgs).foreach { case (param, arg) =>
          callEnv.define(param, arg)
        }
        
        // Execute the body in the new environment
        val callFrame = com.oracle.truffle.api.Truffle.getRuntime.createVirtualFrame(
          Array[AnyRef](callEnv), new FrameDescriptor()
        )
        function.body.execute(callFrame)
        
      case builtin: BuiltinFunction =>
        // Evaluate arguments
        val evaluatedArgs = args.map(_.execute(frame))
        builtin.apply(evaluatedArgs)
        
      case other =>
        throw new RuntimeException(s"Cannot call non-function value: $other")
    }
  }
}

// ============== Builtin Functions ==============

/**
 * Base class for builtin functions (like +, -, *, /)
 */
trait BuiltinFunction {
  def apply(args: List[AnyRef]): AnyRef
}

/**
 * Addition operator
 */
class AddFunction extends BuiltinFunction {
  override def apply(args: List[AnyRef]): AnyRef = {
    args match {
      case List(a: java.lang.Integer, b: java.lang.Integer) =>
        java.lang.Integer.valueOf(a.intValue() + b.intValue())
      case List(a: String, b: String) =>
        a + b
      case _ =>
        throw new RuntimeException(s"Invalid arguments for +: $args")
    }
  }
}

/**
 * Subtraction operator
 */
class SubFunction extends BuiltinFunction {
  override def apply(args: List[AnyRef]): AnyRef = {
    args match {
      case List(a: java.lang.Integer, b: java.lang.Integer) =>
        java.lang.Integer.valueOf(a.intValue() - b.intValue())
      case _ =>
        throw new RuntimeException(s"Invalid arguments for -: $args")
    }
  }
}

/**
 * Multiplication operator
 */
class MulFunction extends BuiltinFunction {
  override def apply(args: List[AnyRef]): AnyRef = {
    args match {
      case List(a: java.lang.Integer, b: java.lang.Integer) =>
        java.lang.Integer.valueOf(a.intValue() * b.intValue())
      case _ =>
        throw new RuntimeException(s"Invalid arguments for *: $args")
    }
  }
}

/**
 * Division operator
 */
class DivFunction extends BuiltinFunction {
  override def apply(args: List[AnyRef]): AnyRef = {
    args match {
      case List(a: java.lang.Integer, b: java.lang.Integer) =>
        java.lang.Integer.valueOf(a.intValue() / b.intValue())
      case _ =>
        throw new RuntimeException(s"Invalid arguments for /: $args")
    }
  }
}

/**
 * Equality operator
 */
class EqFunction extends BuiltinFunction {
  override def apply(args: List[AnyRef]): AnyRef = {
    args match {
      case List(a, b) =>
        java.lang.Boolean.valueOf(a == b)
      case _ =>
        throw new RuntimeException(s"Invalid arguments for ==: $args")
    }
  }
}

/**
 * Less-than operator
 */
class LtFunction extends BuiltinFunction {
  override def apply(args: List[AnyRef]): AnyRef = {
    args match {
      case List(a: java.lang.Integer, b: java.lang.Integer) =>
        java.lang.Boolean.valueOf(a.intValue() < b.intValue())
      case _ =>
        throw new RuntimeException(s"Invalid arguments for <: $args")
    }
  }
}

/**
 * Greater-than operator
 */
class GtFunction extends BuiltinFunction {
  override def apply(args: List[AnyRef]): AnyRef = {
    args match {
      case List(a: java.lang.Integer, b: java.lang.Integer) =>
        java.lang.Boolean.valueOf(a.intValue() > b.intValue())
      case _ =>
        throw new RuntimeException(s"Invalid arguments for >: $args")
    }
  }
}

// ============== Expression List Node ==============

case class ExprListNode(exprs: List[SimpleLangNode]) extends SimpleLangNode {
  override def execute(frame: VirtualFrame): AnyRef = {
    if (exprs.isEmpty) {
      null
    } else {
      // Execute all expressions, return the last result
      exprs.map(_.execute(frame)).last
    }
  }
}

// ============== Helper Functions ==============

/**
 * Get the environment from a frame.
 */
def getEnvironment(frame: VirtualFrame): Environment = {
  val args = frame.getArguments
  if (args.nonEmpty && args(FrameKeys.ENV_INDEX).isInstanceOf[Environment]) {
    args(FrameKeys.ENV_INDEX).asInstanceOf[Environment]
  } else {
    // Create a root environment with builtin functions
    createRootEnvironment()
  }
}

/**
 * Create a root environment with builtin operators.
 */
def createRootEnvironment(): Environment = {
  val env = new Environment()
  env.define("+", new AddFunction)
  env.define("-", new SubFunction)
  env.define("*", new MulFunction)
  env.define("/", new DivFunction)
  env.define("==", new EqFunction)
  env.define("<", new LtFunction)
  env.define(">", new GtFunction)
  env
}
