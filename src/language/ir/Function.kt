package language.ir

/**
 * Represents a function with void return type and no parameters
 */
class Function : Value(VoidFunctionType, 1) {
    var entry: BasicBlock by operand(0)

    val blocks = mutableListOf<BasicBlock>()

    fun push(block: BasicBlock) {
        if (this.blocks.isEmpty())
            entry = block

        this.blocks += block
    }

    fun fullString() = blocks.joinToString("\n\n") { it.fullString() }
}

object VoidFunctionType : Type {
    override fun toString() = "fun"
}