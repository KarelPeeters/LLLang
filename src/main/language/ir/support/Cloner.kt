package language.ir.support

import language.ir.BasicBlock
import language.ir.Function
import language.ir.Instruction
import language.ir.Program
import language.ir.User
import language.ir.Value

object Cloner {
    /**
     * Deep clone [program], replacing uses of functions with the corresponding clones.
     */
    fun cloneProgram(program: Program): Program {
        val cloner = ClonerImpl()
        val newProgram = cloner.flatCloneProgram(program)
        cloner.doReplacements()
        return newProgram
    }

    /**
     * Deep clone [func], replacing uses of blocks, instructions and the function itself with the clones.
     */
    fun cloneFunction(func: Function): Function {
        val cloner = ClonerImpl()
        val newFunction = cloner.flatCloneFunction(func)
        cloner.doReplacements()
        return newFunction
    }

    /**
     * Deep clone [block], replacing uses of instructions and the block itself with the clones.
     */
    fun cloneBlock(block: BasicBlock): BasicBlock {
        val cloner = ClonerImpl()
        val newBlock = cloner.flatCloneBlock(block)
        cloner.doReplacements()
        return newBlock
    }
}

/**
 * Each `flatClone` function adds itself to [users] and optionally to [replaceMap].
 * [doReplacements] then goes trough the users and replaces their operands.
 */
private class ClonerImpl {
    val users = mutableSetOf<User>()
    val replaceMap = mutableMapOf<Value, Value>()

    fun doReplacements() {
        for (user in users)
            for (op in user.operands.toSet())
                user.replaceOperand(op, replaceMap[op] ?: continue)
    }

    fun flatCloneProgram(program: Program): Program {
        val newProgram = Program()
        newProgram.entry = program.entry

        users += newProgram

        for (func in program.functions) {
            val newFunc = flatCloneFunction(func)
            newProgram.addFunction(newFunc)
        }

        return newProgram
    }

    fun flatCloneFunction(func: Function): Function {
        val newFunc = Function(func.name, func.parameters.map { it.name to it.type }, func.returnType, func.attributes)
        newFunc.entry = func.entry

        users += newFunc
        replaceMap[func] = newFunc
        (func.parameters zip newFunc.parameters).toMap(replaceMap)

        for (block in func.blocks) {
            val newBlock = flatCloneBlock(block)
            newFunc.add(newBlock)
        }

        return newFunc
    }

    fun flatCloneBlock(block: BasicBlock): BasicBlock {
        val newBlock = BasicBlock(block.name)

        users += newBlock
        replaceMap[block] = newBlock

        for (instr in block.instructions) {
            val newInstr = flatCloneInstr(instr)
            newBlock.appendOrReplaceTerminator(newInstr)
        }

        return newBlock
    }

    fun flatCloneInstr(instr: Instruction): Instruction {
        val newInstr = instr.clone()

        users += newInstr
        replaceMap[instr] = newInstr

        return newInstr
    }
}
