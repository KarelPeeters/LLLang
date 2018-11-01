package language.optimizer

import language.ir.BasicBlock
import language.ir.Branch
import language.ir.Constant
import language.ir.Function
import language.ir.Jump
import language.ir.Terminator
import language.optimizer.Result.*

object SimplifyBlocks : BlockPass {
    override fun optimize(block: BasicBlock): Result {
        var changed = false

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

                    changed = true
                }
            }
        }

        //remove empty blocks that jump at the end
        run {
            val term = block.terminator
            if (block.instructions.isEmpty() && term is Jump) {
                changed = true
                for (user in block.users.toList()) {
                    when (user) {
                        is Terminator, is Function -> user.replaceOperand(block, term.target)
                        else -> throw IllegalStateException()
                    }
                }
                block.delete()
                return DELETE
            }
        }

        return if (changed) CHANGED else UNCHANGED
    }
}