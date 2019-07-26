package language.ir.support

import language.ir.BasicBlock
import language.ir.Constant
import language.ir.Function
import language.ir.Instruction
import language.ir.ParameterValue
import language.ir.Program
import language.ir.UndefinedValue
import language.ir.UnitValue
import language.ir.Value
import language.ir.visitors.ValueVisitor
import language.util.Graph
import language.util.TraverseOrder.BreadthFirst
import language.util.reachable

fun programEquals(lProg: Program, rProg: Program): Boolean {
    val mappping = buildValueMapping(lProg, rProg) ?: return false

    val map = object : ValueVisitor<Value> {
        override fun invoke(value: Function) = mappping.getValue(value)
        override fun invoke(value: BasicBlock) = mappping.getValue(value)
        override fun invoke(value: Instruction) = mappping.getValue(value)
        override fun invoke(value: ParameterValue) = mappping.getValue(value)

        override fun invoke(value: Constant) = value
        override fun invoke(value: UndefinedValue) = value
        override fun invoke(value: UnitValue) = value
    }

    if (map(lProg.entry) != rProg.entry) return false

    for ((lFunc, rFunc) in lProg.orderedFunctions() zip rProg.orderedFunctions()) {
        if (lFunc.attributes != rFunc.attributes) return false
        if (map(lFunc.entry) != rFunc.entry) return false

        for ((lBlock, rBlock) in lFunc.orderedBlocks() zip rFunc.orderedBlocks()) {
            for ((lInstr, rInstr) in lBlock.instructions zip rBlock.instructions) {
                if (!lInstr.matches(rInstr, map::invoke)) return false
            }
        }
    }

    return true
}

/**
 * Build the mapping of functions, arguments, blocks, and instructions from left tot right.
 * Returns `null` if the mapping is impossible possble the programs are not equal because of size or type difference.
 */
private fun buildValueMapping(lProg: Program, rProg: Program): Map<Value, Value>? {
    val map = mutableMapOf<Value, Value>()

    if (lProg.functions.size != rProg.functions.size) return null
    for ((lFunc, rFunc) in lProg.orderedFunctions() zip rProg.orderedFunctions()) {
        check(map.put(lFunc, rFunc) == null)

        if (lFunc.type != rFunc.type) return null
        for ((lParam, rParam) in lFunc.parameters zip rFunc.parameters) {
            check(map.put(lParam, rParam) == null)
        }

        if (lFunc.blocks.size != rFunc.blocks.size) return null
        for ((lBlock, rBlock) in lFunc.orderedBlocks() zip rFunc.orderedBlocks()) {
            check(map.put(lBlock, rBlock) == null)

            if (lBlock.instructions.size != rBlock.instructions.size) return null
            for ((lInstr, rInstr) in lBlock.instructions zip rBlock.instructions) {
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