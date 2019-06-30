package language.optimizer.passes

import language.ir.BasicBlock
import language.ir.Function
import language.ir.Phi
import language.ir.Terminator
import language.optimizer.FunctionPass
import language.optimizer.OptimizerContext
import language.util.Graph
import language.util.reached


object DeadBlockElimination : FunctionPass() {
    override fun OptimizerContext.optimize(function: Function) {
        val used = object : Graph<BasicBlock> {
            override val roots = setOf(function.entry)
            override fun children(node: BasicBlock) = node.successors()
        }.reached()

        val iter = function.blocks.iterator()
        for (block in iter) {
            if (block !in used) {
                for (user in block.users) {
                    //dead blocks can only be used in phi nodes or in terminators of other dead blocks
                    if (user is Phi)
                        user.sources.remove(block)
                    else
                        require(user is Terminator && user.block !in used)
                }

                block.deepDelete()
                iter.remove()
                changed()
            }
        }
    }
}