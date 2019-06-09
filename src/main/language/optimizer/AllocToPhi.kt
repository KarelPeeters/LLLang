package language.optimizer

import language.ir.Alloc
import language.ir.BasicBlock
import language.ir.Function
import language.ir.Instruction
import language.ir.Load
import language.ir.Phi
import language.ir.Store
import language.ir.Value
import java.util.*

object AllocToPhi : FunctionPass {
    override fun FunctionContext.optimize(function: Function) {
        val variables = function.blocks
                .flatMap { it.instructions.filterIsInstance<Alloc>() }
                .filter { variable -> variable.users.all { it is Store || it is Load } }
        if (variables.isEmpty()) return

        val dom = domInfo()

        for (variable in variables) {
            //remove alloc
            variable.block.remove(variable)
            instrChanged()

            val stores = variable.users.filterIsInstance<Store>()
            val loads = variable.users.filterIsInstance<Load>()

            //insert phi nodes
            val toVisit = stores.mapTo(ArrayDeque()) { it.block }
            val phis = mutableMapOf<BasicBlock, Phi>()

            while (toVisit.isNotEmpty()) {
                val curr = toVisit.pop()

                for (block in dom.frontier(curr)) {
                    if (block !in phis) {
                        toVisit.push(block)

                        val phi = Phi(variable.name, variable.inner)
                        block.add(0, phi)
                        phis[block] = phi
                    }
                }

            }

            /**
             * Find the last value assigned to the current `variable`, backtracking from [use] in [block].
             * If [use] is `null` start from the end of the given block.
             */
            fun findLastValue(block: BasicBlock, use: Instruction?): Value? {
                var curr = block
                while (true) {
                    //drop the instructions after the use instruction
                    val instructions = if (curr == block && use != null)
                        curr.instructions.subList(0, use.indexInBlock())
                    else
                        curr.instructions

                    for (instr in instructions.asReversed()) {
                        when {
                            instr is Phi && phis.containsValue(instr) -> return instr
                            instr is Store && instr.pointer == variable -> return instr.value
                        }
                    }

                    curr = dom.parent(curr) ?: return null
                }
            }


            //remap loads
            for (load in loads) {
                val value = findLastValue(load.block, load) ?: throw NoValueFoundException()
                load.replaceWith(value)
                load.deleteFromBlock()
            }

            val problemPhis = mutableListOf<Phi>()

            //add operands to phi nodes
            for (phi in phis.values) {
                for (pred in phi.block.predecessors()) {
                    val value = findLastValue(pred, null)
                    if (value == null)
                        problemPhis += phi
                    else
                        phi.sources[pred] = value
                }
            }

            //make sure problemPhis aren't used
            for (phi in problemPhis) {
                if (!phi.isUsed())
                    phi.deleteFromBlock()
                else
                    throw NoValueFoundException()
            }

            //remove stores
            for (store in stores) {
                store.deleteFromBlock()
            }
        }
    }

    class NoValueFoundException : Exception()
}
