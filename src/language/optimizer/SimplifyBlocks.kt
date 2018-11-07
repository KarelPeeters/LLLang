package language.optimizer

import language.ir.Function
import language.ir.Jump
import language.ir.Phi
import language.ir.Terminator

object SimplifyBlocks : FunctionPass {
    override fun ChangeTracker.optimize(function: Function) {
        val iter = function.blocks.iterator()

        for (block in iter) {
            //redirect users of empty blocks that end in Jump
            val term = block.terminator
            if (block.instructions.size == 1 && term is Jump) {
                val users = block.users.toList()

                if (users.all { it is Terminator || it is Function }) {
                    for (user in users)
                        user.replaceOperand(block, term.target)
                    changed()
                }
            }

            //remove block if only passively used in Phi instructions
            if (block.users.all { it is Phi }) {
                for (phi in block.users) {
                    (phi as Phi).remove(block)
                }

                block.delete()
                iter.remove()
                changed()
            }
        }
    }
}