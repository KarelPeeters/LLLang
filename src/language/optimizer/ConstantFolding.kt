package language.optimizer

import language.ir.BinaryOp
import language.ir.Branch
import language.ir.Constant
import language.ir.Function
import language.ir.Instruction
import language.ir.Jump
import language.ir.Phi
import language.ir.UnaryOp
import java.util.*

object ConstantFolding : FunctionPass {
    override fun ChangeTracker.optimize(function: Function) {
        val toVisit: Queue<Instruction> = ArrayDeque(function.blocks.flatMap { it.instructions })

        while (toVisit.isNotEmpty()) {
            val curr = toVisit.poll()

            when (curr) {
                is BinaryOp -> {
                    val left = curr.left
                    val right = curr.right

                    if (left is Constant && right is Constant) {
                        changed(curr.block)

                        val result = curr.opType.calculate(left, right)
                        curr.replaceWith(result)
                        curr.deleteFromBlock()
                    }
                }
                is UnaryOp -> {
                    val value = curr.value
                    if (value is Constant) {
                        changed(curr.block)

                        val result = curr.opType.calculate(value)
                        curr.replaceWith(result)
                        curr.deleteFromBlock()
                    }
                }
                is Branch -> {
                    val value = curr.value
                    if (value is Constant) {
                        changed(curr.block)

                        val target = when (value.value) {
                            0 -> curr.ifFalse
                            1 -> curr.ifTrue
                            else -> throw IllegalStateException()
                        }

                        curr.block.terminator = Jump(target)
                        curr.delete()
                    }
                }
                is Phi -> {
                    if (curr.sources.size == 1) {
                        changed(curr.block)

                        val value = curr.sources.values.iterator().next()

                        curr.replaceWith(value)
                        curr.deleteFromBlock()
                    }
                }
            }
        }
    }
}