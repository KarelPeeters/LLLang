package language.ir.support

import language.ir.BasicBlock
import language.ir.Constant
import language.ir.Function
import language.ir.Instruction
import language.ir.ParameterValue
import language.ir.Phi
import language.ir.Program
import language.ir.Return
import language.ir.UndefinedValue
import language.ir.Value
import language.ir.VoidValue
import language.ir.visitors.ValueVisitor
import language.optimizer.DominatorInfo

object Verifier {
    fun verifyProgram(program: Program) {
        //basic checks
        check(!program.isDeleted) { "program was deleted" }
        check(program.operands.none { it.isDeleted })
        check(program.entry in program.functions) { "entry must be one of the functions" }
        check(program.entry.parameters.isEmpty()) { "entry must be a parameterless function" }
        check(program.entry.users == setOf(program)) { "entry can only be used as entry" }

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
        check(function.operands.none { it.isDeleted })
        check(function.entry in function.blocks) { "entry must be one of the blocks" }
        check(function.entry.predecessors().isEmpty()) { "entry can't be jumped to" }
        check(function.parameters.none { it.isDeleted }) { "function parameter deleted" }
        check(function.parameters.all { it.function == function }) { "parameter has wrong function" }

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
        check(block.operands.none { it.isDeleted })
        check(block.instructions.dropWhile { it is Phi }.all { it !is Phi }) { "all phi instructions are at the start of the block" }

        for (instr in block.instructions) {
            check(instr.block == block) { "instruction $instr has wrong block" }
            verifyInstruction(instr)
        }
    }

    fun verifyInstruction(instr: Instruction) {
        check(!instr.isDeleted) { "$instr deleted" }
        check(instr.operands.none { it.isDeleted }) { "$instr operand deleted" }
        instr.typeCheck()
    }
}

private fun verifyOperandDominance(program: Program) {
    for (func in program.functions) {
        val domInfo = DominatorInfo(func)

        val domChecker = object : ValueVisitor<Boolean?> {
            override fun invoke(value: Function) = value in program.functions
            override fun invoke(value: BasicBlock) = value in func.blocks

            override fun invoke(value: Instruction): Boolean? = null
            override fun invoke(value: ParameterValue) = value in func.parameters

            override fun invoke(value: Constant) = true
            override fun invoke(value: UndefinedValue) = true
            override fun invoke(value: VoidValue) = true
        }

        fun checkDominance(instr: Instruction, op: Value) {
            val dominated = domChecker(op) ?: domInfo.isStrictlyDominatedBy(instr, op as Instruction)
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
