package language.optimizer

import language.ir.AggregateValue
import language.ir.BinaryOp
import language.ir.Branch
import language.ir.Constant
import language.ir.Function
import language.ir.GetValue
import language.ir.Instruction
import language.ir.Jump
import language.ir.Phi
import language.ir.UnaryOp
import java.util.*

object ConstantFolding : FunctionPass {
    override fun FunctionContext.optimize(function: Function) {
        val toVisit = ArrayDeque<Instruction>()
        function.blocks.flatMapTo(toVisit) { it.instructions }

        while (toVisit.isNotEmpty()) {
            val curr = toVisit.poll()
            val visitUsers = visit(curr)

            if (visitUsers)
                toVisit.addAll(curr.users.filterIsInstance<Instruction>())
        }
    }

    private fun FunctionContext.visit(instr: Instruction): Boolean {
        when (instr) {
            is BinaryOp -> {
                val left = instr.left
                val right = instr.right

                if (left is Constant && right is Constant) {
                    val result = instr.opType.calculate(left, right)
                    instr.replaceWith(result)
                    instr.deleteFromBlock()

                    instrChanged()
                    return true
                }
            }
            is UnaryOp -> {
                val value = instr.value
                if (value is Constant) {
                    val result = instr.opType.calculate(value)
                    instr.replaceWith(result)
                    instr.deleteFromBlock()

                    instrChanged()
                    return true
                }
            }
            is Branch -> {
                val value = instr.value

                val target = when {
                    value is Constant -> when (value.value) {
                        0 -> instr.ifFalse
                        1 -> instr.ifTrue
                        else -> throw IllegalStateException()
                    }
                    instr.ifTrue == instr.ifFalse -> instr.ifTrue
                    else -> null
                }

                if (target != null) {
                    instr.block.terminator = Jump(target)
                    instr.shallowDelete()

                    graphChanged()
                }
            }
            is Phi -> {
                if (instr.sources.size == 1 || instr.sources.values.distinct().size == 1) {
                    val value = instr.sources.values.iterator().next()
                    instr.replaceWith(value)
                    instr.deleteFromBlock()

                    instrChanged()
                    return true
                }
            }
            is GetValue -> {
                val target = instr.target
                if (target is AggregateValue) {
                    val value = target.values[instr.index]
                    instr.replaceWith(value)
                    instr.deleteFromBlock()

                    instrChanged()
                    return true
                }
            }
            else -> Unit
        }

        return false
    }
}