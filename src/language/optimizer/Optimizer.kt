package language.optimizer

import language.ir.Function

interface OptimizerContext {
    fun instrChanged()
    fun blocksChanged()
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

    override fun blocksChanged() {
        hasChanged = true
        domInfo = null
    }

    override fun domInfo(): DominatorInfo {
        domInfo?.let { return it }

        val start = System.currentTimeMillis()
        val info = DominatorInfo(function)
        println(System.currentTimeMillis() - start)
        domInfo = info
        return info
    }
}

class Optimizer(val verify: Boolean) {
    private val passes = listOf(
            ConstantFolding,
            DeadInstructionElimination,
            SimplifyBlocks,
            DeadBlockElimination
    )

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