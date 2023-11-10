package simplelang

import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.Node

trait SimpleLangNode {
  def execute(frame: VirtualFrame): AnyRef
}

case class IntLitNode(value: Int) extends SimpleLangNode {
  override def execute(frame: VirtualFrame): AnyRef = value.asInstanceOf[AnyRef]
}

case class IfNode(
    cond: SimpleLangNode,
    thenBranch: SimpleLangNode,
    elseBranch: SimpleLangNode
) extends SimpleLangNode {
  override def execute(frame: VirtualFrame): AnyRef = {
    val condValue = cond.execute(frame).asInstanceOf[Boolean]
    if (condValue) thenBranch.execute(frame) else elseBranch.execute(frame)
  }
}

// case class LetNode(name: String, value: SimpleLangNode, body: SimpleLangNode)
//     extends SimpleLangNode {
//   override def execute(frame: VirtualFrame): AnyRef = {
//     val valueEvaluated = value.execute(frame)
//     val newFrame = frame.materialize().copy()
//     newFrame.setObject(
//       newFrame.getFrameDescriptor.findOrAddFrameSlot(name),
//       valueEvaluated
//     )
//     body.execute(newFrame)
//   }
// }

// case class IdentNode(name: String) extends SimpleLangNode {
//   override def execute(frame: VirtualFrame): AnyRef = {
//     frame.getValue(frame.getFrameDescriptor.findFrameSlot(name))
//   }
// }

// case class FnNode(params: List[String], body: SimpleLangNode)
//     extends SimpleLangNode {
//   override def execute(frame: VirtualFrame): AnyRef = {
//     new SimpleFunction(params, body, frame.materialize())
//   }
// }

// case class CallNode(fn: SimpleLangNode, args: List[SimpleLangNode])
//     extends SimpleLangNode {
//   override def execute(frame: VirtualFrame): AnyRef = {
//     val function = fn.execute(frame).asInstanceOf[SimpleFunction]
//     val newFrame = function.newFrame()
//     for ((param, argNode) <- function.params.zip(args)) {
//       newFrame.setObject(
//         newFrame.getFrameDescriptor.findOrAddFrameSlot(param),
//         argNode.execute(frame)
//       )
//     }
//     function.body.execute(newFrame)
//   }
// }

// class SimpleFunction(
//     val params: List[String],
//     val body: SimpleLangNode,
//     val lexicalScope: VirtualFrame
// ) {
//   def newFrame(): VirtualFrame = {
//     val newFrame = lexicalScope.copy()
//     for (param <- params) {
//       newFrame.getFrameDescriptor.findOrAddFrameSlot(param)
//     }
//     newFrame
//   }
// }
