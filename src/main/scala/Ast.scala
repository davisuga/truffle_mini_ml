package simplelang

object Ast:
  enum TypedExpr[T]:
    case IntLit(value: Int) extends TypedExpr[Int]
    case StrLit(value: String) extends TypedExpr[String]
    case BoolLit(value: Boolean) extends TypedExpr[Boolean]
    case If(
        cond: TypedExpr[Boolean],
        thenBranch: TypedExpr[T],
        elseBranch: TypedExpr[T]
    ) extends TypedExpr[T]
    case Fn(params: List[String], body: TypedExpr[T]) extends TypedExpr[T => T]
    case Call[In, Out](fn: TypedExpr[In => Out], args: List[TypedExpr[In]])
        extends TypedExpr[Out]
    case Let(name: String, value: TypedExpr[T], body: TypedExpr[T])
        extends TypedExpr[T]
    case Ident(name: String) extends TypedExpr[T]

  enum Expr:
    case IntLit(value: Int)
    case StrLit(value: String)
    case BoolLit(value: Boolean)
    case If(cond: Expr, thenBranch: Expr, elseBranch: Expr)
    case Fn(params: List[String], body: Expr)
    case Call(fn: Expr, args: List[Expr])
    case Let(name: String, value: Expr, body: Expr)
    case Ident(name: String)
