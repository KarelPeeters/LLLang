package language.optimizer.passes

import language.ir.Alloc
import language.ir.BasicBlock
import language.ir.Call
import language.ir.Function
import language.ir.Jump
import language.ir.Phi
import language.ir.Program
import language.ir.Return
import language.ir.Terminator
import language.ir.support.Cloner
import language.optimizer.OptimizerContext
import language.optimizer.ProgramPass

object FunctionInlining : ProgramPass() {
    override fun OptimizerContext.optimize(program: Program) {
        val iter = program.functions.iterator()
        for (func in iter) {
            if (Function.Attribute.NoInline in func.attributes)
                continue

            //only used by calls, also guaratees all Calls have an immediate Function target
            if (func.users.any { it !is Call || func in it.arguments })
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

                check(!func.isUsed())

                func.deepDelete()
                iter.remove()
            }
        }
    }

    private fun inline(call: Call) {
        val target = call.target as Function
        val containingFunction = call.block.function

        val targetClone = Cloner.cloneFunction(target)
        val beforeBlock = call.block
        val afterBlock = BasicBlock(null)

        //replace parameters
        for ((param, arg) in targetClone.parameters.zip(call.arguments))
            param.replaceWith(arg)

        //insert new blocks
        val nextI = beforeBlock.indexInFunction()
        containingFunction.addAll(nextI + 1, targetClone.blocks)
        containingFunction.add(nextI + 1 + targetClone.blocks.size, afterBlock)

        //aggregate returns
        val returnPhi = Phi(null, targetClone.returnType)
        afterBlock.append(returnPhi)
        for (block in targetClone.blocks) {
            val term = block.terminator
            if (term is Return) {
                returnPhi.sources[block] = term.value
                block.setTerminator(null)
                block.terminator = Jump(afterBlock)
            }
        }
        call.replaceWith(returnPhi)

        //split code before and after call
        val callIndex = call.indexInBlock()
        beforeBlock.remove(call)
        val iter = beforeBlock.basicInstructions.subList(callIndex, beforeBlock.basicInstructions.size).iterator()
        for (instr in iter) {
            afterBlock.append(instr)
            iter.remove()
        }

        afterBlock.terminator = beforeBlock.terminator
        beforeBlock.setTerminator(null)
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
        require(!call.isUsed())
        require(targetClone.parameters.all { !it.isUsed() })
        call.delete()
        targetClone.delete()
    }

    private fun BasicBlock.takeAllocs(): List<Alloc> {
        val result = mutableListOf<Alloc>()

        val iter = basicInstructions.iterator()
        for (instr in iter) {
            if (instr is Alloc) {
                result.add(instr)
                instr.setBlock(null)
                iter.remove()
            }
        }

        return result
    }
}