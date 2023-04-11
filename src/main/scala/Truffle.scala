import com.oracle.truffle.api.nodes.Node

abstract class ExpressionNode extends Node {}

case class AddNode(left: ExpressionNode, right: ExpressionNode)
    extends ExpressionNode {}

case class SubNode(left: ExpressionNode, right: ExpressionNode)
    extends ExpressionNode {}

case class IntLiteralNode(value: Int) extends ExpressionNode {}
