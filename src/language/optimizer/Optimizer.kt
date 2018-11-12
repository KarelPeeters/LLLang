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

class Optimizer {
    private val passes = listOf(
            ConstantFolding,
            DeadInstructionElimination,
            SimplifyBlocks,
            DeadBlockElimination
    )

    fun optimize(function: Function) {
        allocToPhi(function)
        val changeTracker = ChangeTrackerImpl()

        do {
            changeTracker.hasChanged = false
            for (pass in passes) {
                pass.apply { changeTracker.optimize(function) }
            }
        } while (changeTracker.hasChanged)
    }
}