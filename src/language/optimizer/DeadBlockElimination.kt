package language.optimizer

import language.ir.BasicBlock
import language.ir.Function
import java.util.*

object DeadBlockElimination : FunctionPass {
    override fun ChangeTracker.optimize(function: Function) {
        val toVisit: Queue<BasicBlock> = ArrayDeque()
        toVisit.add(function.entry)
        val used = mutableSetOf<BasicBlock>()

        while (toVisit.isNotEmpty()) {
            val curr = toVisit.poll()
            if (curr in used)
                continue

            used += curr
            toVisit.addAll(curr.successors())
        }

        val iter = function.blocks.iterator()
        for (block in iter) {
            if (block !in used) {
                iter.remove()
                block.delete()
                changed()
            }
        }
    }
}