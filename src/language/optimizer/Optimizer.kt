package language.optimizer

import language.ir.BasicBlock
import language.ir.Function
import language.optimizer.Result.DELETE
import language.optimizer.Result.UNCHANGED

enum class Result {
    UNCHANGED, CHANGED, DELETE
}

interface BlockPass {
    fun optimize(block: BasicBlock): Result
}

interface FunctionPass {
    fun optimize(function: Function): Result
}

class Optimizer {
    private val basicBlockPasses = listOf<BlockPass>(SimplifyBlocks)
    private val functionPasses = listOf<FunctionPass>()

    fun optimize(function: Function) {
        AllocToPhi.optimize(function)

        do {
            var changed = false

            basicBlockPasses.forEach { pass ->
                function.blocks.removeAll { block ->
                    val result = pass.optimize(block)
                    changed = changed || result != UNCHANGED
                    result == DELETE
                }
            }

            functionPasses.forEach { pass ->
                val result = pass.optimize(function)
                changed = changed || result != UNCHANGED
                require(result != DELETE)
            }
        } while (changed)
    }
}