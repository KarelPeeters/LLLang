package language.optimizer.passes

import language.ir.Function
import language.ir.Instruction
import language.optimizer.FunctionPass
import language.optimizer.OptimizerContext
import language.util.Graph
import language.util.reachable

object DeadInstructionElimination : FunctionPass() {
    override fun OptimizerContext.optimize(function: Function) {
        val used = object : Graph<Instruction> {
            override val roots = function.blocks.flatMap { it.instructions }.filter { !it.pure }
            override fun children(node: Instruction) = node.operands.filterIsInstance<Instruction>().toList()
        }.reachable()

        for (block in function.blocks) {
            val iter = block.basicInstructions.iterator()
            for (instr in iter) {
                if (instr !in used) {
                    instr.delete()
                    iter.remove()
                    changed()
                }
            }
        }
    }
}