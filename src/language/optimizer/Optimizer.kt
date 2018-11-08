package language.optimizer

import language.ir.BasicBlock
import language.ir.Function

interface ChangeTracker {
    fun changed()
    fun changed(block: BasicBlock)
}

interface FunctionPass {
    fun ChangeTracker.optimize(function: Function)
}

private class ChangeTrackerImpl : ChangeTracker {
    var hasChanged = false

    override fun changed() {
        hasChanged = true
    }

    override fun changed(block: BasicBlock) = changed()
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