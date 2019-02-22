package language.optimizer

import language.ir.Function
import language.ir.Program

object DeadFunctionElimination : ProgramPass {
    override fun ProgramContext.optimize(program: Program) {
        val used = object : Graph<Function> {
            override val roots = setOf(program.entry)
            override fun children(node: Function) = node.blocks
                    .flatMap { f -> f.instructions.flatMap { i -> i.operands } }
                    .filterIsInstance<Function>()
        }.reached()

        val iter = program.functions.iterator()
        for (func in iter) {
            if (func !in used) {
                require(func.users.isEmpty())

                func.deepDelete()
                iter.remove()
                changed()
            }
        }
    }
}