package language.optimizer

import language.ir.BasicBlock
import language.ir.Function
import language.ir.Jump

object SimplifyBlocks : BodyPass {
    override fun optimize(function: Function): Boolean {
        val deadBlocks = mutableListOf<BasicBlock>()

        for (block in function.blocks) {
            val term = block.terminator
            if (block.instructions.isEmpty() && term is Jump) {
                block.users.forEach { user -> user.replaceValue(block, term.target) }

                deadBlocks += block
            }
        }

        function.blocks.removeAll(deadBlocks)
        return deadBlocks.isNotEmpty()
    }
}