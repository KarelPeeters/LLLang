package language.optimizer.passes

import language.ir.AggregateValue
import language.ir.Alloc
import language.ir.BasicBlock
import language.ir.BinaryOp
import language.ir.Blur
import language.ir.Branch
import language.ir.Call
import language.ir.Constant
import language.ir.Eat
import language.ir.Exit
import language.ir.Function
import language.ir.GetSubPointer
import language.ir.GetSubValue
import language.ir.Instruction
import language.ir.Jump
import language.ir.Load
import language.ir.ParameterValue
import language.ir.Phi
import language.ir.Program
import language.ir.Return
import language.ir.Store
import language.ir.UnaryOp
import language.ir.UndefinedValue
import language.ir.Value
import language.ir.VoidType
import language.ir.VoidValue
import language.ir.visitors.ValueVisitor
import language.optimizer.OptimizerContext
import language.optimizer.ProgramPass
import java.util.*

/**
 * This pass combines interprocedural dead instruction and signature elimination.
 *
 * Starts by assuming only the operands of certain impure instructions are used, works backwards to find all used
 * instructions and parameters, then does a pass over the program and removes everything that isn't used.
 *
 * It's recommended that dead blocks and functions are removed from the program before running this pass, as it
 * conservatively assumes all of the code in program actually runs.
 */
object DeadCodeElimination : ProgramPass() {
    override fun OptimizerContext.optimize(program: Program) {
        val (used, usedReturn) = findUsed(program)

        val funcIter = program.functions.listIterator()

        for (func in funcIter) {
            val usedParams = func.parameters.map { it in used }
            val usedRet = func.returnType is VoidType || func in usedReturn
            funcIter.set(removeDeadSignature(func, usedParams, usedRet))

            for (block in func.blocks) {
                val instrIter = block.basicInstructions.listIterator()
                for (instr in instrIter) {
                    if (instr !is Call && instr !in used) {
                        instr.delete()
                        instrIter.remove()
                    }
                }
            }
        }
    }
}

/**
 * Calculates
 * * the set of instructions, functions-as-value, parameters and possibly other values used
 * * the set of functions whose return type is used.
 */
private fun findUsed(program: Program): Pair<Set<Value>, Set<Function>> {
    val doneUsed = mutableSetOf<Value>()
    val doneUsedReturn = mutableSetOf<Function>()

    val used = ArrayDeque<Value>(usageRoots(program))
    val usedReturn = ArrayDeque<Function>()

    val vistor = object : ValueVisitor<Unit> {
        //function used as non-call target, safe assumption: make the returns used
        override fun invoke(value: Function) {
            usedReturn += value
        }

        //basicblock used, this shouldn't happend
        override fun invoke(value: BasicBlock) {
            error("BasicBlocks shoudln't be visited")
        }

        //instruction result used
        override fun invoke(value: Instruction) {
            if (value is Call) {
                val target = value.target
                if (target is Function) {
                    //immediate target call, make target used but mark arguments used imediatly
                    usedReturn += target
                } else {
                    //unknown target, just make everything used (including the target value)
                    used.addAll(value.operands)
                }
            } else {
                used += value.operands
            }
        }

        //parameter used, mark call arguments used
        override fun invoke(value: ParameterValue) {
            val function = value.function
            val index = function.parameters.indexOf(value)

            for (user in function.users)
                if (user is Call && user.target == function)
                    used += user.arguments[index]
        }

        override fun invoke(value: Constant) {}
        override fun invoke(value: UndefinedValue) {}
        override fun invoke(value: VoidValue) {}
    }

    while (used.isNotEmpty() || usedReturn.isNotEmpty()) {
        while (true) {
            val curr = used.poll() ?: break
            if (doneUsed.add(curr))
                vistor(curr)
        }

        while (true) {
            val curr = usedReturn.poll() ?: break
            if (doneUsedReturn.add(curr))
                used.addAll(curr.returnInstructions())
        }
    }

    return doneUsed to doneUsedReturn
}

/**
 * Returns the set of instructions in the program that use their operands even if their resulting value isn't used and
 * need to stay in the program independant of usage.
 */
private fun usageRoots(program: Program): List<Value> = sequence {
    for (func in program.functions) {
        for (block in func.blocks) {
            for (instr in block.instructions) {
                when (instr) {
                    is Call -> {
                        //can't yield a Function nor a Call because that would immediatly mark the return as used
                        val target = instr.target
                        if (target !is Function) yield(target)
                    }
                    is Branch -> yield(instr.value)
                    is Eat, is Store -> yield(instr)

                    is Alloc, is Load,
                    is BinaryOp, is UnaryOp,
                    is Phi, is Blur,
                    is GetSubValue, is GetSubPointer, is AggregateValue,
                    is Jump, is Exit, is Return -> Unit
                }
            }
        }
    }
}.toList()
