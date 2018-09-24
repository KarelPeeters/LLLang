package language.optimizer

import language.ir.BasicBlock
import language.ir.Jump
import language.optimizer.Result.DELETE
import language.optimizer.Result.UNCHANGED

object SimplifyBlocks : BlockPass {
    override fun optimize(block: BasicBlock): Result {
        val term = block.terminator
        if (block.instructions.isEmpty() && term is Jump) {
            block.replaceWith(term.target)
            return DELETE
        }

        return UNCHANGED
    }
}