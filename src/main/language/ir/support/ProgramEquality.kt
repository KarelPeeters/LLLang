package language.ir.support

import language.ir.*
import language.ir.Function
import language.ir.visitors.ValueVisitor
import language.util.Graph
import language.util.TraverseOrder.BreadthFirst
import language.util.reachable

fun programEquals(lProg: Program, rProg: Program): Boolean {
    val mappping = buildValueMapping(lProg, rProg) ?: return false

    val rReachable = mappping.values.toSet()

    /**
     * Converts left values to correspoonding right values to be used in an `==` check. `null` means the left value is
     * unreachable and should just compare equal to everthing.
     */
    val mapper = object : ValueVisitor<Value?> {
        override fun invoke(value: Function) = mappping[value]
        override fun invoke(value: BasicBlock) = mappping[value]
        override fun invoke(value: Instruction) = mappping[value]
        override fun invoke(value: ParameterValue) = mappping[value]

        override fun invoke(value: Constant) = value
        override fun invoke(value: UndefinedValue) = value
        override fun invoke(value: VoidValue) = value
    }

    val maps = object : ValueMapper {
        override fun invoke(left: Value, right: Value): Boolean {
            check(left.type == right.type)

            return when (val mapped = mapper(left)) {
                //left is not reachable, so just make sure right is not reachable either
                null -> right !in rReachable
                else -> right == mapped
            }
        }

        override fun invoke(left: List<Value>, right: List<Value>): Boolean =
                left.zip(right, this::invoke).all { it }

        override fun invoke(left: Set<Value>, right: Set<Value>): Boolean {
            if (left.size != right.size) return false

            val leftMapped = left.mapNotNull { mapper(it) }
            val rightFiltered = right.filter { it in rReachable }

            return leftMapped == rightFiltered
        }

        override fun invoke(left: Map<out Value, Value>, right: Map<out Value, Value>): Boolean {
            if (left.size != right.size) return false

            val leftMapped: Map<Value, Value> = left.mapNotNull { (k, v) ->
                mapper(k)?.let { it to (mapper(v) ?: error("usage of unreachable value?")) }
            }.toMap()
            val rightFiltered = right.filterKeys { it in rReachable }

            return leftMapped == rightFiltered
        }
    }

    if (!maps(lProg.entry, rProg.entry)) return false

    for ((lFunc, rFunc) in lProg.orderedFunctions() zip rProg.orderedFunctions()) {
        if (lFunc.attributes != rFunc.attributes) return false
        if (!maps(lFunc.entry, rFunc.entry)) return false

        for ((lBlock, rBlock) in lFunc.orderedBlocks() zip rFunc.orderedBlocks()) {
            for ((lInstr, rInstr) in lBlock.instructions zip rBlock.instructions) {
                if (!lInstr.matches(rInstr, maps)) return false
            }
        }
    }

    return true
}

/**
 * Build the mapping of functions, arguments, blocks, and instructions from left tot right.
 * Only includes the reachable functions/blocks/instructions.
 * Returns `null` if the mapping is impossible possble the programs are not equal because of size or type difference.
 */
private fun buildValueMapping(lProg: Program, rProg: Program): Map<Value, Value>? {
    val map = mutableMapOf<Value, Value>()

    val lFuncs = lProg.orderedFunctions()
    val rFuncs = rProg.orderedFunctions()
    if (rFuncs.size != lFuncs.size) return null

    for ((lFunc, rFunc) in lFuncs zip rFuncs) {
        check(map.put(lFunc, rFunc) == null)

        if (lFunc.type != rFunc.type) return null
        for ((lParam, rParam) in lFunc.parameters zip rFunc.parameters) {
            check(map.put(lParam, rParam) == null)
        }

        val lBlocks = lFunc.orderedBlocks()
        val rBlocks = rFunc.orderedBlocks()
        if (lBlocks.size != rBlocks.size) return null

        for ((lBlock, rBlock) in lBlocks zip rBlocks) {
            check(map.put(lBlock, rBlock) == null)

            val lInstrs = lBlock.instructions
            val rInstrs = rBlock.instructions
            if (lInstrs.size != rInstrs.size) return null

            for ((lInstr, rInstr) in lInstrs zip rInstrs) {
                if (lInstr.type != rInstr.type) return null
                check(map.put(lInstr, rInstr) == null)
            }
        }
    }

    return map
}

private fun Program.orderedFunctions() = object : Graph<Function> {
    override val roots = listOf(entry)
    override fun children(node: Function) = node.orderedBlocks().flatMap { block ->
        block.instructions.flatMap { instr ->
            instr.operands.filterIsInstance<Function>()
        }
    }
}.reachable(BreadthFirst)

private fun Function.orderedBlocks() = object : Graph<BasicBlock> {
    override val roots = listOf(entry)
    override fun children(node: BasicBlock): List<BasicBlock> = node.successors()
}.reachable(BreadthFirst)