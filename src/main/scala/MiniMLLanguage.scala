package simplelang

import com.oracle.truffle.api._
import com.oracle.truffle.api.frame.{FrameDescriptor, VirtualFrame}
import com.oracle.truffle.api.nodes.{Node, RootNode}
import com.oracle.truffle.api.interop._
import com.oracle.truffle.api.source.Source

import scala.collection.mutable

/**
 * MiniML Truffle Language Implementation.
 * 
 * This is the main entry point for the Truffle language runtime.
 * It handles language registration, parsing, and context management.
 */
@TruffleLanguage.Registration(
  id = "miniml",
  name = "MiniML",
  defaultMimeType = "application/x-miniml",
  characterMimeTypes = Array("application/x-miniml"),
  contextPolicy = TruffleLanguage.ContextPolicy.SHARED
)
class MiniMLLanguage extends TruffleLanguage[MiniMLContext] {
  
  override def createContext(env: TruffleLanguage.Env): MiniMLContext = {
    new MiniMLContext(this, env)
  }
  
  override def parse(request: TruffleLanguage.ParsingRequest): CallTarget = {
    val source = request.getSource
    val rootNode = parseSource(source)
    rootNode.getCallTarget
  }
  
  @CompilerDirectives.TruffleBoundary
  private def parseSource(source: Source): MiniMLRootNode = {
    val code = source.getCharacters.toString
    val parseResult = Parser.parseInput(code)
    
    parseResult match {
      case fastparse.Parsed.Success(ast, _) =>
        val truffleNode = AstToTruffle.toTruffleNode(ast)
        new MiniMLRootNode(this, truffleNode, source)
      case failure: fastparse.Parsed.Failure =>
        throw new RuntimeException(s"Parse error: ${failure.msg}")
    }
  }
  
  override def getScope(context: MiniMLContext): Object = {
    context.getGlobalScope
  }
}

object MiniMLLanguage {
  val ID = "miniml"
  val MIME_TYPE = "application/x-miniml"
  
  /**
   * Get the current context.
   */
  def getCurrentContext: MiniMLContext = {
    TruffleLanguage.getCurrentContext(classOf[MiniMLLanguage])
  }
}

/**
 * The context for a MiniML execution.
 * 
 * Manages the global scope, polyglot bindings, and environment.
 */
class MiniMLContext(
    val language: MiniMLLanguage,
    val env: TruffleLanguage.Env
) {
  // Root environment with builtins
  val rootEnv: Environment = createRootEnvironmentWithInterop()
  
  // Global scope object for interop
  private val globalScope = new MiniMLScope(rootEnv)
  
  def getGlobalScope: MiniMLScope = globalScope
  
  /**
   * Create the root environment with builtins and interop functions.
   */
  private def createRootEnvironmentWithInterop(): Environment = {
    val env = createRootEnvironment()
    
    // Add interop builtins
    env.define("polyglotImport", new PolyglotImportFunction(this))
    env.define("polyglotExport", new PolyglotExportFunction(this))
    env.define("javaType", new JavaTypeFunction(this))
    env.define("invoke", new InvokeFunction())
    env.define("getMember", new GetMemberFunction())
    env.define("setMember", new SetMemberFunction())
    env.define("newInstance", new NewInstanceFunction())
    
    env
  }
  
  /**
   * Import a value from polyglot bindings.
   */
  def polyglotImport(name: String): AnyRef = {
    val bindings = env.getPolyglotBindings
    val interop = InteropLibrary.getUncached
    if (interop.isMemberReadable(bindings, name)) {
      interop.readMember(bindings, name).asInstanceOf[AnyRef]
    } else {
      throw new RuntimeException(s"Polyglot binding '$name' not found")
    }
  }
  
  /**
   * Export a value to polyglot bindings.
   */
  def polyglotExport(name: String, value: AnyRef): Unit = {
    val bindings = env.getPolyglotBindings
    val interop = InteropLibrary.getUncached
    interop.writeMember(bindings, name, value)
  }
  
  /**
   * Lookup a Java type by name.
   */
  def lookupJavaType(name: String): AnyRef = {
    if (env.isHostLookupAllowed) {
      env.lookupHostSymbol(name).asInstanceOf[AnyRef]
    } else {
      throw new RuntimeException("Host class lookup is not allowed. Enable with allowHostClassLookup(true)")
    }
  }
}

/**
 * Root node for MiniML programs.
 */
class MiniMLRootNode(
    language: MiniMLLanguage,
    val bodyNode: SimpleLangNode,
    source: Source
) extends RootNode(language) {
  
  override def execute(frame: VirtualFrame): AnyRef = {
    val context = MiniMLLanguage.getCurrentContext
    
    // Create execution frame with root environment
    val execArgs = Array[AnyRef](context.rootEnv)
    val execFrame = com.oracle.truffle.api.Truffle.getRuntime.createVirtualFrame(
      execArgs, new FrameDescriptor()
    )
    
    bodyNode.execute(execFrame)
  }
  
  override def getName: String = "<main>"
}

/**
 * Global scope object exposed to other languages.
 * 
 * NOTE: In a full Truffle implementation with Java annotation processor,
 * this would use @ExportLibrary(InteropLibrary.class) to enable interop.
 * Since Scala doesn't support the annotation processor, we provide
 * a simpler implementation for manual use.
 */
class MiniMLScope(val env: Environment) extends TruffleObject {
  
  def hasMembers: Boolean = true
  
  def getMembers: MiniMLMemberKeys = {
    new MiniMLMemberKeys(getMemberNames)
  }
  
  def isMemberReadable(member: String): Boolean = {
    try {
      env.lookup(member)
      true
    } catch {
      case _: RuntimeException => false
    }
  }
  
  def readMember(member: String): AnyRef = {
    try {
      wrapForInterop(env.lookup(member))
    } catch {
      case e: RuntimeException =>
        throw UnknownIdentifierException.create(member)
    }
  }
  
  def isMemberModifiable(member: String): Boolean = isMemberReadable(member)
  
  def isMemberInsertable(member: String): Boolean = !isMemberReadable(member)
  
  def writeMember(member: String, value: AnyRef): Unit = {
    env.define(member, unwrapFromInterop(value))
  }
  
  /**
   * Get all member names from the environment.
   */
  private def getMemberNames: Array[String] = {
    // Return the builtin operator names
    Array("+", "-", "*", "/", "==", "<", ">")
  }
  
  /**
   * Wrap a MiniML value for interop (make it accessible to other languages).
   */
  private def wrapForInterop(value: AnyRef): AnyRef = value match {
    case fn: SimpleFunction => new InteropFunction(fn)
    case other => other
  }
  
  /**
   * Unwrap a value received from interop.
   */
  private def unwrapFromInterop(value: AnyRef): AnyRef = value match {
    case fn: InteropFunction => fn.function
    case other => other
  }
}

/**
 * Interop-enabled wrapper for member keys.
 * This provides array-like access to member names.
 */
class MiniMLMemberKeys(val keys: Array[String]) extends TruffleObject {
  
  def hasArrayElements: Boolean = true
  
  def getArraySize: Long = keys.length
  
  def isArrayElementReadable(index: Long): Boolean = 
    index >= 0 && index < keys.length
  
  def readArrayElement(index: Long): AnyRef = {
    if (isArrayElementReadable(index)) {
      keys(index.toInt)
    } else {
      throw InvalidArrayIndexException.create(index)
    }
  }
  
  def toArray: Array[String] = keys
}

/**
 * Interop-enabled wrapper for MiniML functions.
 * 
 * This allows MiniML functions to be called from other Truffle languages
 * and from Java via the Polyglot API.
 * 
 * Since we can't use the annotation processor, we provide
 * public methods that can be called directly for testing.
 */
class InteropFunction(val function: SimpleFunction) extends TruffleObject {
  
  def isExecutable: Boolean = true
  
  def execute(arguments: Array[AnyRef]): AnyRef = {
    if (arguments.length != function.params.length) {
      throw ArityException.create(function.params.length, function.params.length, arguments.length)
    }
    
    // Create environment for the call
    val callEnv = function.closureEnv.extend()
    function.params.zip(arguments).foreach { case (param, arg) =>
      callEnv.define(param, arg)
    }
    
    // Execute the function body
    val frame = com.oracle.truffle.api.Truffle.getRuntime.createVirtualFrame(
      Array[AnyRef](callEnv), new FrameDescriptor()
    )
    function.body.execute(frame)
  }
  
  def hasMembers: Boolean = true
  
  def getMembers: MiniMLMemberKeys = {
    new MiniMLMemberKeys(Array("arity"))
  }
  
  def isMemberReadable(member: String): Boolean = member == "arity"
  
  def readMember(member: String): AnyRef = member match {
    case "arity" => java.lang.Integer.valueOf(function.params.length)
    case _ => throw UnknownIdentifierException.create(member)
  }
  
  def getArity: Int = function.params.length
  
  override def toString: String = s"<function(${function.params.mkString(", ")})>"
}

// ============== Interop Builtin Functions ==============

/**
 * Import a value from polyglot bindings.
 * Usage: polyglotImport "name"
 */
class PolyglotImportFunction(context: MiniMLContext) extends BuiltinFunction {
  override def apply(args: List[AnyRef]): AnyRef = {
    args match {
      case List(name: String) => context.polyglotImport(name)
      case _ => throw new RuntimeException("polyglotImport expects a string argument")
    }
  }
}

/**
 * Export a value to polyglot bindings.
 * Usage: polyglotExport "name" value
 */
class PolyglotExportFunction(context: MiniMLContext) extends BuiltinFunction {
  override def apply(args: List[AnyRef]): AnyRef = {
    args match {
      case List(name: String, value) =>
        context.polyglotExport(name, value.asInstanceOf[AnyRef])
        value.asInstanceOf[AnyRef]
      case _ => throw new RuntimeException("polyglotExport expects name and value arguments")
    }
  }
}

/**
 * Lookup a Java type by name.
 * Usage: javaType "java.util.ArrayList"
 */
class JavaTypeFunction(context: MiniMLContext) extends BuiltinFunction {
  override def apply(args: List[AnyRef]): AnyRef = {
    args match {
      case List(name: String) => context.lookupJavaType(name)
      case _ => throw new RuntimeException("javaType expects a string argument")
    }
  }
}

/**
 * Invoke a method on a foreign object.
 * Usage: invoke obj "methodName" arg1 arg2 ...
 */
class InvokeFunction extends BuiltinFunction {
  override def apply(args: List[AnyRef]): AnyRef = {
    args match {
      case obj :: (methodName: String) :: methodArgs =>
        val interop = InteropLibrary.getUncached
        if (interop.isMemberInvocable(obj, methodName)) {
          interop.invokeMember(obj, methodName, methodArgs.toArray: _*).asInstanceOf[AnyRef]
        } else {
          throw new RuntimeException(s"Method '$methodName' is not invocable on $obj")
        }
      case _ => throw new RuntimeException("invoke expects object, method name, and optional arguments")
    }
  }
}

/**
 * Get a member from a foreign object.
 * Usage: getMember obj "fieldName"
 */
class GetMemberFunction extends BuiltinFunction {
  override def apply(args: List[AnyRef]): AnyRef = {
    args match {
      case List(obj, memberName: String) =>
        val interop = InteropLibrary.getUncached
        if (interop.isMemberReadable(obj, memberName)) {
          interop.readMember(obj, memberName).asInstanceOf[AnyRef]
        } else {
          throw new RuntimeException(s"Member '$memberName' is not readable on $obj")
        }
      case _ => throw new RuntimeException("getMember expects object and member name")
    }
  }
}

/**
 * Set a member on a foreign object.
 * Usage: setMember obj "fieldName" value
 */
class SetMemberFunction extends BuiltinFunction {
  override def apply(args: List[AnyRef]): AnyRef = {
    args match {
      case List(obj, memberName: String, value) =>
        val interop = InteropLibrary.getUncached
        if (interop.isMemberWritable(obj, memberName)) {
          interop.writeMember(obj, memberName, value)
          value.asInstanceOf[AnyRef]
        } else {
          throw new RuntimeException(s"Member '$memberName' is not writable on $obj")
        }
      case _ => throw new RuntimeException("setMember expects object, member name, and value")
    }
  }
}

/**
 * Create a new instance of a foreign class.
 * Usage: newInstance JavaClass arg1 arg2 ...
 */
class NewInstanceFunction extends BuiltinFunction {
  override def apply(args: List[AnyRef]): AnyRef = {
    args match {
      case clazz :: constructorArgs =>
        val interop = InteropLibrary.getUncached
        if (interop.isInstantiable(clazz)) {
          interop.instantiate(clazz, constructorArgs.toArray: _*).asInstanceOf[AnyRef]
        } else {
          throw new RuntimeException(s"$clazz is not instantiable")
        }
      case _ => throw new RuntimeException("newInstance expects a class and optional constructor arguments")
    }
  }
}
