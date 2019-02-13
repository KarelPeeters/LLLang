package language.optimizer

import language.ir.Function
import language.ir.Program

interface ProgramPass {
    fun ProgramContext.optimize(program: Program)
}

interface ProgramContext {
    fun changed()
}

interface FunctionPass {
    fun FunctionContext.optimize(function: Function)
}

interface FunctionContext {
    fun instrChanged()
    fun graphChanged()
    fun domInfo(): DominatorInfo
}

private class ProgramContextImpl(val program: Program) : ProgramContext {
    var hasChanged = false

    override fun changed() {
        hasChanged = true
    }
}

private class FunctionContextImpl(val function: Function) : FunctionContext {
    var hasChanged = false
    var domInfo: DominatorInfo? = null

    override fun instrChanged() {
        hasChanged = true
    }

    override fun graphChanged() {
        hasChanged = true
        domInfo = null
    }

    override fun domInfo(): DominatorInfo {
        domInfo?.let { return it }

        val info = DominatorInfo(function)
        domInfo = info
        return info
    }
}

class Optimizer(var doVerify: Boolean = true) {
    private val programPasses: List<ProgramPass> = listOf(
            DeadFunctionElimination,
            FunctionInlining
    )

    private val functionPasses: List<FunctionPass> = listOf(
            AllocToPhi,
            ConstantFolding,
            DeadInstructionElimination,
            SimplifyBlocks,
            DeadBlockElimination
    )

    fun optimize(program: Program) {
        fun verify(subject: Any?, pass: Any?) {
            if (doVerify) {
                try {
                    program.verify()
                } catch (e: Exception) {
                    throw IllegalStateException("verify fail after pass $pass on $subject", e)
                }
            }
        }

        verify(null, null)

        val programContext = ProgramContextImpl(program)
        val functionContexts = program.functions.associate { it to FunctionContextImpl(it) }

        do {
            //program passes
            for (pass in programPasses) {
                pass.apply { programContext.optimize(program) }
                verify(program, pass)
            }

            //function passes
            for (function in program.functions) {
                val context = functionContexts.getValue(function)
                for (pass in functionPasses) {
                    pass.apply { context.optimize(function) }
                    verify(function, pass)
                }
            }

            //changed
            var changed = programContext.hasChanged
            programContext.hasChanged = false
            for (context in functionContexts.values) {
                changed = changed || context.hasChanged
                context.hasChanged = false
            }
        } while (changed)
    }
}