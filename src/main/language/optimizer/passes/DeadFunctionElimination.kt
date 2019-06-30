package language.optimizer.passes

import language.ir.Function
import language.ir.Program
import language.optimizer.OptimizerContext
import language.optimizer.ProgramPass
import language.util.Graph
import language.util.reached

object DeadFunctionElimination : ProgramPass() {
    override fun OptimizerContext.optimize(program: Program) {
        val used = object : Graph<Function> {
            override val roots = setOf(program.entry)
            override fun children(node: Function) = node.blocks
                    .flatMap { f -> f.instructions.flatMap { i -> i.operands } }
                    .filterIsInstance<Function>()
        }.reached()

        val iter = program.functions.iterator()
        for (func in iter) {
            if (func !in used) {
                require(!func.isUsed())

                func.deepDelete()
                iter.remove()
                changed()
            }
        }
    }
}