package language.optimizer

import language.ir.Function
import language.ir.Program

sealed class OptimizerPass {
    abstract fun OptimizerContext.runOnProgram(program: Program, afterPass: (subject: Any?) -> Unit)

    override fun toString(): String = this.javaClass.simpleName
}

abstract class ProgramPass : OptimizerPass() {
    abstract fun OptimizerContext.optimize(program: Program)

    override fun OptimizerContext.runOnProgram(program: Program, afterPass: (subject: Any?) -> Unit) {
        optimize(program)
        afterPass(program)
    }
}

abstract class FunctionPass : OptimizerPass() {
    abstract fun OptimizerContext.optimize(function: Function)

    override fun OptimizerContext.runOnProgram(program: Program, afterPass: (subject: Any?) -> Unit) {
        for (function in program.functions) {
            optimize(function)
            afterPass(function)
        }
    }
}

interface OptimizerContext {
    fun changed()
    fun domInfo(function: Function): DominatorInfo
}

private class OptimizerContextImpl : OptimizerContext {
    var hasChanged = false

    override fun changed() {
        hasChanged = true
    }

    override fun domInfo(function: Function) = DominatorInfo(function)
}

val DEFAULT_PASSES = listOf(
        //program passes
        DeadFunctionElimination,
        DeadSignatureElimination,
        FunctionInlining,

        //function passes
        SplitAggregate,
        AllocToPhi,
        ConstantFolding,
        DeadInstructionElimination,
        SimplifyBlocks,
        DeadBlockElimination
)

class Optimizer(
        private val passes: Iterable<OptimizerPass> = DEFAULT_PASSES,
        private val repeat: Boolean = true,
        private val doVerify: Boolean = true
) {
    private val _runPasses = mutableListOf<OptimizerPass>()
    val runPasses: List<OptimizerPass> get() = _runPasses

    private fun verify(program: Program, pass: Any?, subject: Any?) {
        if (doVerify) {
            try {
                program.verify()
            } catch (e: Exception) {
                throw IllegalStateException("verify fail after pass $pass on $subject", e)
            }
        }
    }

    fun optimize(program: Program) {
        verify(program, null, null)

        val context = OptimizerContextImpl()

        while (true) {
            context.hasChanged = false

            for (pass in passes) {
                _runPasses += pass

                with(pass) {
                    context.runOnProgram(program) {
                        verify(program, pass, it)
                    }
                }
            }

            if (!repeat || !context.hasChanged) break
        }
    }
}