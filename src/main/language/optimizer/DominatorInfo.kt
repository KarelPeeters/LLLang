package language.optimizer

import language.ir.BasicBlock
import language.ir.Function

private fun calcDominatedBy(function: Function): Map<BasicBlock, Set<BasicBlock>> {
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

class DominatorInfo(val function: Function) {
    private val dominatedBy: Map<BasicBlock, Set<BasicBlock>> = calcDominatedBy(function)

    private val domParent: Map<BasicBlock, BasicBlock?> = function.blocks.associate { block ->
        val blockDoms = dominatedBy.getValue(block).filter { it != block }
        block to blockDoms.find { cand ->
            val candDoms = dominatedBy.getValue(cand)
            blockDoms.all { it in candDoms }
        }
    }

    private val frontiers: Map<BasicBlock, Set<BasicBlock>> = function.blocks.associate { block ->
        block to dominatedBy.asSequence()
                .mapNotNull { (k, v) -> if (block in v) k else null }
                .flatMap { it.successors().asSequence() }
                .filter { candidate -> block !in dominatedBy.getValue(candidate) }
                .toSet()
    }

    fun domBy(block: BasicBlock, by: BasicBlock) = by in dominatedBy.getValue(block)

    fun parent(block: BasicBlock) = domParent.getValue(block)

    fun frontier(block: BasicBlock) = frontiers.getValue(block)
}