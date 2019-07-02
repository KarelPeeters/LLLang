package language.optimizer.passes

import language.ir.Function
import language.ir.Jump
import language.ir.Phi
import language.ir.Terminator
import language.optimizer.FunctionPass
import language.optimizer.OptimizerContext


object SimplifyBlocks : FunctionPass() {
    override fun OptimizerContext.optimize(function: Function) {
        val iter = function.blocks.iterator()

        for (block in iter) {
            //redirect users of empty blocks that end in Jump
            val term = block.terminator

            if (block.basicInstructions.isEmpty() && term is Jump && term.target != block &&
                !(block == function.entry && term.target.predecessors().isNotEmpty())
            ) {
                val users = block.users.toList()

                if (users.all { it is Terminator || it is Function }) {
                    for (user in users)
                        user.replaceOperand(block, term.target)

                    changed()
                    block.deepDelete()
                    iter.remove()
                    continue
                }
            }

            //move code into only pred block
            val preds = block.predecessors()
            if (block != function.entry && preds.size == 1) {
                val pred = preds.first()
                val predTerm = pred.terminator

                if (pred != block && predTerm is Jump) {
                    for (instr in block.basicInstructions) {
                        pred.append(instr)
                    }
                    pred.terminator.delete()
                    pred.terminator = block.terminator

                    block.users.toList().forEach {
                        (it as Phi).replaceOperand(block, pred)
                    }

                    changed()
                    iter.remove()
                    continue
                }
            }
        }
    }
}