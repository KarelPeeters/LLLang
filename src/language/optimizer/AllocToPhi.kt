package language.optimizer

import language.ir.Alloc
import language.ir.BasicBlock
import language.ir.Function
import language.ir.Instruction
import language.ir.Load
import language.ir.Phi
import language.ir.Store
import language.ir.Value
import language.optimizer.Result.UNCHANGED
import test.NAME_ENV
import java.lang.Exception
import java.lang.reflect.AnnotatedTypeVariable

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

fun <K, V> Map<K, Set<V>>.reverseMap(allKeys: Iterable<V>) = allKeys.associate { key ->
    key to this.filterValues { key in it }.keys
}

object AllocToPhi : FunctionPass {
    override fun optimize(function: Function): Result {
        val dominatedBy: Map<BasicBlock, Set<BasicBlock>> = calcDominatedBy(function)
        val dominates = dominatedBy.reverseMap(function.blocks)

        val domParent = function.blocks.associate { block ->
            val blockDoms = dominatedBy.getValue(block).filter { it != block }
            block to blockDoms.find { cand ->
                val candDoms = dominatedBy.getValue(cand)
                blockDoms.all { it in candDoms }
            }
        }

        val frontiers: Map<BasicBlock, Set<BasicBlock>> = function.blocks.associate { block ->
            block to dominates.getValue(block)
                    .asSequence()
                    .flatMap { it.successors().asSequence() }
                    .filter { candidate -> candidate !in dominates.getValue(block) }
                    .toSet()
        }
        val frontieredBy = frontiers.reverseMap(function.blocks)

        val env = NAME_ENV
//        println("[[Dominated by]]")
//        println(dominatedBy.toList().joinToString("\n") { (k, v) -> "${env.block(k)} <- \t ${v.joinToString { env.block(it) }}" })
//        println("[[Dominates]]")
//        println(dominates.toList().joinToString("\n") { (k, v) -> "${env.block(k)} -> \t ${v.joinToString { env.block(it) }}" })
//        println("[[Frontier]]")
//        println(frontiers.toList().joinToString("\n") { (k, v) -> "${env.block(k)} ->| \t ${v.joinToString { env.block(it) }}" })
//        println("[[Frontiered by]]")
//        println(frontieredBy.toList().joinToString("\n") { (k, v) -> "${env.block(k)} |<- \t ${v.joinToString { env.block(it) }}" })
//        println("[[DomTree parent]")
//        println(domParent.toList().joinToString("\n") { (k, v) -> "${env.block(k)} ~> \t ${v?.let { env.block(it) }}" })

        val variables = function.blocks.flatMap { it.instructions.filterIsInstance<Alloc>() }

        for (variable in variables) {
            //println("before fixing ${variable.name}")
            //println("====================================")
            //println(function.fullStr(NAME_ENV))
            //println()
            //println()

            //remove alloc
            variable.block.remove(variable)

            val stores = variable.users.filterIsInstance<Store>()
            val loads = variable.users.filterIsInstance<Load>()
            val phis = mutableMapOf<BasicBlock, Phi>()

            fun findLastValue(block: BasicBlock, use: Instruction?): Value {
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

                    curr = domParent[curr]
                            ?: throw NoValueFoundException()
                }
            }

            //insert phi nodes
            for (store in stores) {
                for (block in frontiers.getValue(store.block)) {
                    if (block !in phis) {
                        val phi = Phi(variable.name, variable.inner)
                        block.insertAt(0, phi)
                        phis[block] = phi
                    }
                }
            }

            //remap loads
            for (load in loads) {
                val value = findLastValue(load.block, load)
                load.replaceWith(value)
                load.block.remove(load)
            }

            //add operands to phi nodes
            for (phi in phis.values) {
                if (phi.users.isEmpty()) {
                    phi.block.remove(phi)
                } else {
                    for (pred in phi.block.predecessors()) {
                        phi.set(pred, findLastValue(pred, null))
                    }
                }
            }

            //remove stores
            for (store in stores) {
                store.block.remove(store)
            }
        }

        return UNCHANGED

        /*
        1) insert phi functions at every node of the dominator frontier for EVERY variable
        2) remap usages to phi nodes

        - Don't attempt to put in minimal phi nodes, let other passes remove them instead
        - Allocate Maps etc, just get it done for now!
         */
    }
}

class NoValueFoundException: Exception()