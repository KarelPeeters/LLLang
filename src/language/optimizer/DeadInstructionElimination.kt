package language.optimizer

import language.ir.Function
import language.ir.Instruction

object DeadInstructionElimination : FunctionPass {
    override fun FunctionContext.optimize(function: Function) {
        val used = object : Graph<Instruction> {
            override val roots = function.blocks.flatMap { it.instructions }.filter { !it.pure }
            override fun children(node: Instruction) = node.operands.filterIsInstance<Instruction>()
        }.reached()

        for (block in function.blocks) {
            for (instr in block.instructions.toList()) {
                if (instr !in used) {
                    instr.deleteFromBlock()
                    instrChanged()
                }
            }
        }
    }
}