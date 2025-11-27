package simplelang

import Ast._

/**
 * Converts AST expressions to Truffle nodes for execution.
 */
object AstToTruffle {
  
  def toTruffleNode(ast: Expr): SimpleLangNode = ast match {
    case Expr.IntLit(value) => 
      IntLitNode(value)
    
    case Expr.StrLit(value) => 
      StrLitNode(value)
    
    case Expr.BoolLit(value) => 
      BoolLitNode(value)
    
    case Expr.If(cond, thenBranch, elseBranch) =>
      IfNode(
        toTruffleNode(cond),
        toTruffleNode(thenBranch),
        toTruffleNode(elseBranch)
      )
    
    case Expr.Let(name, value, body) =>
      LetNode(name, toTruffleNode(value), toTruffleNode(body))
    
    case Expr.Ident(name) => 
      IdentNode(name)
    
    case Expr.Fn(params, body) => 
      FnNode(params, toTruffleNode(body))
    
    case Expr.Call(fn, args) =>
      CallNode(toTruffleNode(fn), args.map(toTruffleNode))
    
    case Expr.ExprList(exprs) =>
      ExprListNode(exprs.map(toTruffleNode))
  }
}
