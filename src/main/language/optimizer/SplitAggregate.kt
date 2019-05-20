package language.optimizer

import language.ir.AggregateType
import language.ir.AggregateValue
import language.ir.Alloc
import language.ir.Constant
import language.ir.Function
import language.ir.GetSubPointer
import language.ir.GetSubValue
import language.ir.Instruction
import language.ir.Load
import language.ir.Store

object SplitAggregate : FunctionPass {
    override fun FunctionContext.optimize(function: Function) {
        for (alloc in function.allocs()) {
            val type = alloc.inner as? AggregateType ?: continue

            if (!alloc.users.all {
                        it is Load || it is Store || it is GetSubPointer.Struct ||
                        (it is GetSubPointer.Array && it.index is Constant)
                    })
                continue

            val replacements = type.innerTypes.map { Alloc(null, it) }
            for (user in alloc.users.toList()) {
                when (user) {
                    is Load -> {
                        val loads = replacements.map { Load(null, it) }
                        val newValue = AggregateValue(null, type, loads)
                        user.block.addAll(user.indexInBlock(), loads)
                        user.block.add(user.indexInBlock(), newValue)
                        user.replaceWith(newValue)
                        user.deleteFromBlock()
                    }
                    is Store -> {
                        val instructions = mutableListOf<Instruction>()

                        for ((i, repl) in replacements.withIndex()) {
                            val value = GetSubValue.getFixedIndex(user.value, i)
                            instructions += value
                            instructions += Store(repl, value)
                        }

                        user.block.addAll(user.indexInBlock(), instructions)
                        user.deleteFromBlock()
                    }
                    is GetSubPointer.Struct -> {
                        user.replaceWith(replacements[user.index])
                        user.deleteFromBlock()
                    }
                    is GetSubPointer.Array -> {
                        val index = (user.index as Constant).value
                        user.replaceWith(replacements[index])
                        user.deleteFromBlock()
                    }
                    else -> error("can't happen")
                }
            }

            instrChanged()

            val allocIndex = alloc.indexInBlock()
            alloc.block.addAll(allocIndex, replacements)
            alloc.deleteFromBlock()
        }
    }
}