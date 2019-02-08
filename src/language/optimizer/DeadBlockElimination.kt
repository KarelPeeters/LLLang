package language.optimizer

import language.ir.BasicBlock
import language.ir.Function
import language.ir.Phi


object DeadBlockElimination : FunctionPass {
    override fun FunctionContext.optimize(function: Function) {
        val used = object : Graph<BasicBlock> {
            override val roots = setOf(function.entry)
            override fun children(node: BasicBlock) = node.successors()
        }.reached()

        val iter = function.blocks.iterator()
        for (block in iter) {
            if (block !in used) {
                for (user in block.users) {
                    require(user is Phi)
                    user.remove(block)
                }

                iter.remove()
                block.delete(true)
                graphChanged()
            }
        }
    }
}