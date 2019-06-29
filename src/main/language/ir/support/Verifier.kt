package language.ir.support

import language.ir.BasicBlock
import language.ir.Constant
import language.ir.Function
import language.ir.Instruction
import language.ir.ParameterValue
import language.ir.Phi
import language.ir.Program
import language.ir.Return
import language.ir.UnitValue
import language.ir.Value
import language.optimizer.DominatorInfo

object Verifier {
    fun verifyProgram(program: Program) {
        //basic checks
        check(!program.isDeleted) { "program was deleted" }
        check(program.entry in program.functions) { "entry must be one of the functions" }
        check(program.entry.parameters.isEmpty()) { "entry must be a parameterless function" }

        //duplicate checking
        val allBlocks = program.functions.flatMap { it.blocks }
        val allInstructions = program.functions.flatMap { f -> f.blocks.flatMap { b -> b.instructions } }
        allBlocks.checkNoDuplicates { "found duplicate blocks $it" }
        allInstructions.checkNoDuplicates { "found duplicate instructions $it" }

        //functing checking
        for (func in program.functions) {
            check(func.program == program) { "function $func has wrong program" }
            verifyFunction(func)
        }

        //full operand dominance checking, only possible at this level
        verifyOperandDominance(program)
    }

    fun verifyFunction(function: Function) {
        //basic checks
        check(!function.isDeleted) { "function was deleted" }
        check(function.entry in function.blocks) { "entry must be one of the blocks" }
        check(function.entry.predecessors().isEmpty()) { "entry can't be jumped to" }

        //return type check
        val allReturns = function.blocks.map { it.terminator }.filterIsInstance<Return>()
        for (ret in allReturns) {
            check(ret.value.type == function.returnType) { "return ${ret.value.type} doesn't match function return type ${function.returnType}" }
        }

        //block checking
        for (block in function.blocks) {
            check(block.function == function) { "block $block has wrong function" }
            verifyBlock(block)
        }
    }

    fun verifyBlock(block: BasicBlock) {
        //basic checks
        check(!block.isDeleted) { "block was deleted" }
        check(block.instructions.dropWhile { it is Phi }.all { it !is Phi }) { "all phi instructions are at the start of the block" }

        for (instr in block.instructions) {
            check(instr.block == block) { "instruction $instr has wrong block" }
            verifyInstruction(instr)
        }
    }

    fun verifyInstruction(instr: Instruction) {
        check(!instr.isDeleted)
        instr.typeCheck()
    }
}

private fun verifyOperandDominance(program: Program) {
    for (func in program.functions) {
        val domInfo = DominatorInfo(func)

        fun checkDominance(instr: Instruction, op: Value) {
            val dominated = when (op) {
                is Instruction -> domInfo.isStrictlyDominatedBy(instr, op)
                is Function -> op in program.functions
                is BasicBlock -> op in func.blocks
                is ParameterValue -> op in func.parameters
                is Constant, is UnitValue -> true
                else -> error("Unknown optype ${op::class.java}")
            }

            check(dominated) { "value $op doesn't dominate user $instr" }
        }

        for (block in func.blocks) {
            for (instr in block.instructions) {
                if (instr is Phi) {
                    for ((prevBlock, value) in instr.sources) {
                        checkDominance(prevBlock.terminator, value)
                    }
                } else {
                    for (op in instr.operands) {
                        checkDominance(instr, op)
                    }
                }
            }
        }
    }
}


private fun <T> Iterable<T>.checkNoDuplicates(error: (Set<T>) -> String) {
    val set = mutableSetOf<T>()
    val dupes = mutableSetOf<T>()

    for (v in this) {
        if (!set.add(v))
            dupes.add(v)
    }

    check(dupes.isEmpty()) { error(dupes) }
}