package language.optimizer

import language.ir.BasicBlock
import language.ir.Function

interface BlockPass {
    fun optimize(block: BasicBlock): Boolean
}

interface BodyPass {
    fun optimize(function: Function): Boolean
}

class Optimizer {
    private val basicBlockPasses = listOf<BlockPass>()
    private val bodyPasses = listOf<BodyPass>(
            SimplifyBlocks
    )

    fun optimize(function: Function) {
        do {
            var cont = false

            basicBlockPasses.forEach { pass ->
                function.blocks.forEach { block ->
                    cont = pass.optimize(block) || cont
                }
            }

            bodyPasses.forEach { pass ->
                cont = pass.optimize(function) || cont
            }
        } while (cont)
    }
}