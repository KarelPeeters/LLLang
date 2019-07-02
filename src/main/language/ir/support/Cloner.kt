package language.ir.support

import language.ir.BasicBlock
import language.ir.Function
import language.ir.Program
import language.ir.Value

object Cloner {
    /**
     * Deep clone [program], replacing uses of functions with the corresponding clones.
     */
    fun cloneProgram(program: Program): Program {
        val replaceMap = mutableMapOf<Value, Value>()

        //shallow clone
        val newProgram = Program()
        newProgram.entry = program.entry

        for (func in program.functions) {
            val newFunc = cloneFunction(func)
            newProgram.addFunction(newFunc)
            replaceMap[func] = newFunc
        }

        //replace uses
        val users = sequence {
            yield(newProgram)
            yieldAll(newProgram.functions)
            for (func in newProgram.functions) {
                yieldAll(func.blocks)
                for (block in func.blocks)
                    yieldAll(block.instructions)
            }
        }

        for (user in users) {
            for ((old, new) in replaceMap)
                user.replaceOperand(old, new)
        }

        return newProgram
    }

    /**
     * Deep clone [func], replacing uses of blocks, instructions and the function itself with the clones.
     */
    fun cloneFunction(func: Function): Function {
        val replaceMap = mutableMapOf<Value, Value>()

        //shallow clone
        val newFunc = Function(func.name, func.parameters.map { it.name to it.type }, func.returnType, func.attributes)
        newFunc.entry = func.entry

        replaceMap[func] = newFunc
        (func.parameters zip newFunc.parameters).toMap(replaceMap)

        for (block in func.blocks) {
            val newBlock = cloneBlock(block)
            newFunc.add(newBlock)

            replaceMap[block] = newBlock
            (block.instructions zip newBlock.instructions).toMap(replaceMap)
        }

        //replace uses
        //all Users are conveniently also part of the replaceMap so we can just iterate over the keys
        for (user in replaceMap.values)
            for (op in user.operands.toSet())
                user.replaceOperand(op, replaceMap[op] ?: continue)

        return newFunc
    }
}

/**
 * Doesn't replace any uses, it's easier to let that happen at the function level. The resulting semantics are strange,
 * so this function is kept private.
 */
private fun cloneBlock(block: BasicBlock): BasicBlock {
    val newBlock = BasicBlock(block.name)
    for (instr in block.basicInstructions) {
        newBlock.append(instr.clone())
    }
    newBlock.terminator = block.terminator.clone()
    return newBlock
}
