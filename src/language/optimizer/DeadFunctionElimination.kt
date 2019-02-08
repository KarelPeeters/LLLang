package language.optimizer

import language.ir.Function
import language.ir.Program

object DeadFunctionElimination : ProgramPass {
    override fun ProgramContext.optimize(program: Program) {
        val used = object : Graph<Function> {
            override val roots = setOf(program.entry)
            override fun children(node: Function) =
                    node.blocks.flatMap { it.instructions.flatMap { it.operands } }.filterIsInstance<Function>()
        }.reached()

        for (func in program.functions.toList()) {
            if (func !in used) {
                func.delete()
                program.functions -= func
            }
        }
    }
}