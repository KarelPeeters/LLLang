package language.optimizer

import language.ir.Function
import language.ir.Program
import language.ir.support.Visitor

interface OptimizerContext {
    fun changed()
}

interface ProgramPass {
    fun OptimizerContext.optimize(program: Program)
}

interface FunctionPass : ProgramPass {
    fun OptimizerContext.optimize(function: Function)

    override fun OptimizerContext.optimize(program: Program) {
        for (function in Visitor.findFunctions(program))
            optimize(function)
    }
}

class Optimizer(
        val passes: List<ProgramPass>
) {
    fun optimize(program: Program) {
        do {
            var changed = false
            for (pass in passes) {
                if (runPass(pass, program))
                    changed = true
            }
        } while (changed)
    }
}

private fun runPass(pass: ProgramPass, program: Program): Boolean = with(pass) {
    val context = object : OptimizerContext {
        var changed = false
        override fun changed() {
            changed = true
        }
    }
    context.optimize(program)
    context.changed
}