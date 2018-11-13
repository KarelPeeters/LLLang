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

fun calcDominatedBy(function: Function): Map<BasicBlock, Set<BasicBlock>> {
    val result = function.blocks.associateTo(mutableMapOf()) { it to function.blocks.toMutableSet() }
    result[function.entry] = mutableSetOf(function.entry)

    do {
        var changed = false
        for ((block, domSet) in result) {
            for (pred in block.predecessors()) {
                //if something got removed
                if (domSet.retainAll(result.getValue(pred) + setOf(block)))
                    changed = true
            }
        }
    } while (changed)

    return result
}


fun allocToPhi(function: Function) {
    val dominatedBy: Map<BasicBlock, Set<BasicBlock>> = calcDominatedBy(function)

    val domParent = function.blocks.associate { block ->
        val blockDoms = dominatedBy.getValue(block).filter { it != block }
        block to blockDoms.find { cand ->
            val candDoms = dominatedBy.getValue(cand)
            blockDoms.all { it in candDoms }
        }
    }

    val frontiers: Map<BasicBlock, Set<BasicBlock>> = function.blocks.associate { block ->
        block to dominatedBy.asSequence()
                .mapNotNull { (k, v) -> if (block in v) k else null }
                .flatMap { it.successors().asSequence() }
                .filter { candidate -> block !in dominatedBy.getValue(candidate) }
                .toSet()
    }

    val variables = function.blocks
            .flatMap { it.instructions.filterIsInstance<Alloc>() }
            .filter { variable -> variable.users.all { it is Store || it is Load } }

    for (variable in variables) {
        //remove alloc
        variable.block.remove(variable)

        val stores = variable.users.filterIsInstance<Store>()
        val loads = variable.users.filterIsInstance<Load>()

        //insert phi nodes
        val toVisit = stores.mapTo(LinkedList()) { it.block }
        val phis = mutableMapOf<BasicBlock, Phi>()

        while (toVisit.isNotEmpty()) {
            val curr = toVisit.pop()

            for (block in frontiers.getValue(curr!!)) {
                if (block !in phis) {
                    toVisit.push(block)

                    val phi = Phi(variable.name, variable.inner)
                    block.insertAt(0, phi)
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
                    curr.instructions.subList(0, curr.instructions.indexOf(use))
                else
                    curr.instructions

                for (instr in instructions.asReversed()) {
                    when {
                        instr is Phi && phis.containsValue(instr) -> return instr
                        instr is Store && instr.pointer == variable -> return instr.value
                    }
                }

                curr = domParent[curr] ?: return null
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
                    phi.set(pred, value)
            }
        }

        //make sure problemPhis aren't used
        for (phi in problemPhis) {
            if (phi.users.isEmpty()) {
                phi.deleteFromBlock()
            } else
                throw NoValueFoundException()
        }

        //remove stores
        for (store in stores) {
            store.deleteFromBlock()
        }
    }
}

class NoValueFoundException : Exception()