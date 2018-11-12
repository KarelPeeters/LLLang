package language.optimizer

import language.ir.Function
import language.ir.Instruction
import java.util.*

object DeadInstructionElimination : FunctionPass {
    override fun ChangeTracker.optimize(function: Function) {
        val toVisit: Queue<Instruction> = ArrayDeque(function.blocks.flatMap { it.instructions }.filter { !it.pure })
        val used = mutableSetOf<Instruction>()

        while (toVisit.isNotEmpty()) {
            val curr = toVisit.poll()
            if (curr in used)
                continue

            used += curr
            toVisit.addAll(curr.operands.filterIsInstance<Instruction>())
        }

        for (block in function.blocks) {
            for (instr in block.instructions.toList()) {
                if (instr !in used) {
                    instr.deleteFromBlock()
                    changed()
                }
            }
        }
    }
}