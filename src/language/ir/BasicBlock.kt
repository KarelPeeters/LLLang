package language.ir

/**
 * A list of instructions with a Terminator at the end. No control flow happens within a BasicBlock.
 */
class BasicBlock(val name: String) : Value(BlockType) {
    override val replaceAble = false

    val instructions = mutableListOf<Instruction>()
    lateinit var terminator: Terminator

    fun insertAt(index: Int, instruction: Instruction): Instruction {
        this.instructions.add(index, instruction)
        return instruction
    }

    fun append(instruction: Instruction): Instruction {
        this.instructions += instruction
        return instruction
    }

    override fun toString() = "<$name>"

    fun fullString() = instructions.joinToString(
            separator = "", prefix = "$this\n", postfix = "$terminator"
    ) { "${it.fullString()}\n" }
}

object BlockType : Type() {
    override fun toString() = "block"
}