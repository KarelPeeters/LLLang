package language.optimizer

import language.ir.Function
import language.ir.Instruction

object DeadInstructionElimination : FunctionPass {
    override fun FunctionContext.optimize(function: Function) {
        val used = object : Graph<Instruction> {
            override val roots = function.blocks.flatMap { it.instructions }.filter { !it.pure }
            override fun children(node: Instruction) = node.operands.filterIsInstance<Instruction>().toList()
        }.reached()

        for (block in function.blocks) {
            val iter = block.instructions.iterator()
            for (instr in iter) {
                if (instr !in used) {
                    instr.delete()
                    iter.remove()
                    instrChanged()
                }
            }
        }
    }
}