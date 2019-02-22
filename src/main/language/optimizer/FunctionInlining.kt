package language.optimizer

import language.ir.BasicBlock
import language.ir.Call
import language.ir.Function
import language.ir.Jump
import language.ir.Phi
import language.ir.Program
import language.ir.Return
import language.ir.Terminator

object FunctionInlining : ProgramPass {
    override fun ProgramContext.optimize(program: Program) {
        val iter = program.functions.iterator()
        for (func in iter) {
            //only used by calls, also guaratees all Calls have an immediate Function target
            if (func.users.any { it !is Call })
                continue
            val calls = func.users.map { it as Call }

            //skip immediatly recursive functions
            if (calls.any { it.block.function == func })
                continue

            //inline heuristic
            val instrCount = func.blocks.sumBy { it.instructions.size }
            if (calls.size == 1 || instrCount < 10) {
                for (call in calls) {
                    inline(call)
                    changed()
                }
            }

            if (func.users.isEmpty()) {
                func.deepDelete()
                iter.remove()
            }
        }
    }

    private fun inline(call: Call) {
        val target = call.target as Function
        val targetClone = target.deepClone()
        val containingFunction = call.block.function
        val beforeBlock = call.block
        val afterBlock = BasicBlock(null)

        //replace parameters
        for ((param, arg) in targetClone.parameters.zip(call.arguments))
            param.replaceWith(arg)

        //insert new blocks
        var nextI = containingFunction.blocks.indexOf(beforeBlock)
        for (block in targetClone.blocks)
            containingFunction.add(++nextI, block)
        containingFunction.add(++nextI, afterBlock)

        //aggregate returns
        val returnPhi = Phi(null, targetClone.returnType)
        afterBlock.append(returnPhi)
        for (block in targetClone.blocks) {
            val term = block.terminator
            if (term is Return) {
                returnPhi.set(block, term.value)
                term.deleteFromBlock()
                block.terminator = Jump(afterBlock)
            }
        }
        call.replaceWith(returnPhi)

        //split code before and after call
        val callIndex = beforeBlock.instructions.indexOf(call)
        beforeBlock.remove(call)
        val iter = beforeBlock.instructions.subList(callIndex, beforeBlock.instructions.size).iterator()
        for (instr in iter) {
            afterBlock.appendOrReplaceTerminator(instr)
            iter.remove()
        }
        beforeBlock.terminator = Jump(targetClone.entry)

        //change phi nodes using beforeBlock
        for (user in beforeBlock.users.toList()) {
            when (user) {
                is Phi -> user.replaceOperand(beforeBlock, afterBlock)
                is Terminator, is Function -> Unit
                else -> throw IllegalStateException("unknown user $user of block")
            }
        }

        //cleanup
        require(call.users.isEmpty())
        require(targetClone.parameters.all { it.users.isEmpty() })
        call.shallowDelete()
        targetClone.shallowDelete()
    }
}