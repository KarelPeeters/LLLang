package language.optimizer.passes

import language.ir.Alloc
import language.ir.BasicBlock
import language.ir.BasicInstruction
import language.ir.Function
import language.ir.Load
import language.ir.Phi
import language.ir.Store
import language.ir.UndefinedValue
import language.ir.Value
import language.optimizer.FunctionPass
import language.optimizer.OptimizerContext
import java.util.*

object AllocToPhi : FunctionPass() {
    override fun OptimizerContext.optimize(function: Function) {
        val variables = function.blocks
                .flatMap { it.instructions.filterIsInstance<Alloc>() }
                .filter { variable -> variable.users.all { (it is Store && it.value != variable) || it is Load } }
        if (variables.isEmpty()) return

        val dom = domInfo(function)

        for (variable in variables) {
            //remove alloc
            variable.deleteFromBlock()

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
            fun findLastValue(block: BasicBlock, use: BasicInstruction?): Value? {
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

            val undef = UndefinedValue(variable.inner)

            //remap loads
            for (load in loads) {
                val value = findLastValue(load.block, load) ?: undef
                load.replaceWith(value)
                load.deleteFromBlock()
            }

            //add operands to phi nodes
            for (phi in phis.values) {
                for (pred in phi.block.predecessors()) {
                    val value = findLastValue(pred, null)
                    phi.sources[pred] = value ?: undef
                }
            }

            //remove stores
            for (store in stores) {
                store.deleteFromBlock()
            }

            changed()
        }
    }

    class NoValueFoundException : Exception()
}
