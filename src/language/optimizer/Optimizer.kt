package language.optimizer

import language.ir.Function

interface ChangeTracker {
    fun changed()
}

interface FunctionPass {
    fun ChangeTracker.optimize(function: Function)
}

private class ChangeTrackerImpl : ChangeTracker {
    var hasChanged = false

    override fun changed() {
        hasChanged = true
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

        val changeTracker = ChangeTrackerImpl()
        AllocToPhi.apply { changeTracker.optimize(function) }

        if (verify)
            function.verify()

        do {
            changeTracker.hasChanged = false
            for (pass in passes) {
                pass.apply { changeTracker.optimize(function) }

                if (verify)
                    function.verify()
            }
        } while (changeTracker.hasChanged)
    }
}