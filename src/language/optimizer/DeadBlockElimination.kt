package language.optimizer

import language.ir.BasicBlock
import language.ir.Function
import language.ir.Phi
import java.util.*

object DeadBlockElimination : FunctionPass {
    override fun OptimizerContext.optimize(function: Function) {
        val toVisit: Queue<BasicBlock> = ArrayDeque()
        toVisit.add(function.entry)
        val used = mutableSetOf<BasicBlock>()

        while (toVisit.isNotEmpty()) {
            val curr = toVisit.poll()

            if (used.add(curr))
                toVisit.addAll(curr.successors())
        }

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