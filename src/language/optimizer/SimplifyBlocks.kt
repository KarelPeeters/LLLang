package language.optimizer

import language.ir.Function
import language.ir.Jump
import language.ir.Phi
import language.ir.Terminator

object SimplifyBlocks : FunctionPass {
    override fun FunctionContext.optimize(function: Function) {
        val iter = function.blocks.iterator()

        for (block in iter) {
            //redirect users of empty blocks that end in Jump
            val term = block.terminator
            if (block.instructions.size == 1 && term is Jump && term.target != block) {
                val users = block.users.toList()

                if (users.all { it is Terminator || it is Function }) {
                    for (user in users)
                        user.replaceOperand(block, term.target)
                    graphChanged()
                    continue
                }
            }

            //move code into only pred block
            val preds = block.predecessors()
            if (block != function.entry && preds.size == 1 && preds.first().terminator is Jump) {
                val pred = preds.first()

                for (instr in block.instructions.dropLast(1)) {
                    pred.append(instr)
                }
                pred.terminator.delete()
                pred.terminator = block.terminator

                block.users.toList().forEach {
                    (it as Phi).replaceOperand(block, pred)
                }
                block.shallowDelete()
                iter.remove()
            }
        }
    }
}