package language.optimizer

import language.ir.BasicBlock
import language.ir.Function
import language.ir.Instruction
import language.ir.Terminator
import language.util.Graph
import language.util.reachable

private fun calcDominatedBy(function: Function): Map<BasicBlock, Set<BasicBlock>> {
    val result = function.blocks.associateWithTo(mutableMapOf()) { function.blocks.toMutableSet() }
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

    private val domParent: Map<BasicBlock, BasicBlock?>

    init {
        val reachable = object : Graph<BasicBlock> {
            override val roots = setOf(function.entry)
            override fun children(node: BasicBlock) = node.successors()
        }.reachable()

        domParent = function.blocks.associateWith { block ->
            if (block !in reachable)
                return@associateWith null

            val doms = dominatedBy.getValue(block)
            val strictDoms = doms.filter { it != block }

            strictDoms.find { cand ->
                val candDoms = dominatedBy.getValue(cand)
                strictDoms.all { it in candDoms }
            }
        }
    }

    private val frontiers: Map<BasicBlock, Set<BasicBlock>> = function.blocks.associate { block ->
        block to dominatedBy.asSequence()
                .mapNotNull { (k, v) -> if (block in v) k else null }
                .flatMap { it.successors().asSequence() }
                .filter { candidate -> block !in dominatedBy.getValue(candidate) }
                .toSet()
    }

    fun dominators(block: BasicBlock) = dominatedBy.getValue(block)

    fun strictDominators(block: BasicBlock) = dominatedBy.getValue(block) - block

    fun isDominatedBy(block: BasicBlock, by: BasicBlock) = by in dominatedBy.getValue(block)

    fun isStrictlyDominatedBy(block: BasicBlock, by: BasicBlock) = block != by && isDominatedBy(block, by)

    fun isStrictlyDominatedBy(instr: Instruction, by: Instruction): Boolean {
        if (instr == by) return false

        val instrBlock = instr.block
        val byBlock = by.block

        if (isStrictlyDominatedBy(instrBlock, byBlock)) return true
        if (instrBlock != byBlock) return false

        if (instr is Terminator) return true
        if (by is Terminator) return false

        val instrIndex = instrBlock.basicInstructions.indexOf(instr)
        val byIndex = instrBlock.basicInstructions.indexOf(by)

        return instrIndex > byIndex
    }

    fun parent(block: BasicBlock) = domParent.getValue(block)

    fun frontier(block: BasicBlock) = frontiers.getValue(block)
}