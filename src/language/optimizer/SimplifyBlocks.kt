package language.optimizer

import language.ir.BasicBlock
import language.ir.Branch
import language.ir.Constant
import language.ir.Jump
import language.ir.Terminator
import language.optimizer.Result.DELETE
import language.optimizer.Result.UNCHANGED

object SimplifyBlocks : BlockPass {
    override fun optimize(block: BasicBlock): Result {
        //remove certain Branch
        run {
            val term = block.terminator
            if (term is Branch) {
                val target = if (term.ifTrue == term.ifFalse)
                    term.ifTrue
                else {
                    (term.value as? Constant)?.value?.let {
                        if (it == 0) term.ifFalse else term.ifTrue
                    }
                }
                if (target != null) {
                    block.terminator = Jump(target)
                    term.delete()
                }
            }
        }

        //remove empty blocks that jump at the end
        run {
            val term = block.terminator
            if (block.instructions.isEmpty() && term is Jump) {
                for (user in block.users.toList()) {
                    when (user) {
                        is Terminator -> user.replaceOperand(block, term.target)
                        else -> throw IllegalStateException()
                    }
                }
                block.delete()
                return DELETE
            }
        }

        return UNCHANGED
    }
}