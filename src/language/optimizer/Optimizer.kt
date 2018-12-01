package language.optimizer

import language.ir.Function
import language.ir.Program

interface OptimizerContext {
    fun instrChanged()
    fun graphChanged()
    fun domInfo(): DominatorInfo
}

interface FunctionPass {
    fun OptimizerContext.optimize(function: Function)
}

private class OptimizerContextImplt(val function: Function) : OptimizerContext {
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

class Optimizer(val verify: Boolean) {
    private val passes = listOf<FunctionPass>(
            ConstantFolding,
            DeadInstructionElimination,
            SimplifyBlocks,
            DeadBlockElimination
    )

    fun optimize(program: Program) {
        for (function in program.functions) {
            optimize(function)
        }
    }

    fun optimize(function: Function) {
        if (verify)
            function.verify()

        val context = OptimizerContextImplt(function)
        AllocToPhi.apply { context.optimize(function) }

        if (verify)
            function.verify()

        do {
            context.hasChanged = false
            for (pass in passes) {
                pass.apply { context.optimize(function) }

                if (verify)
                    function.verify()
            }
        } while (context.hasChanged)
    }
}