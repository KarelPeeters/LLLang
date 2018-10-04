package language.ir

/**
 * Represents a function with void return type and no parameters
 */
class Function : Value(VoidFunctionType) {
    var entry by operand<BasicBlock>()

    val blocks = mutableListOf<BasicBlock>()

    fun push(block: BasicBlock) {
        if (this.blocks.isEmpty())
            entry = block

        this.blocks += block
    }

    fun fullString() = "entry: $entry\n${blocks.joinToString("\n\n") { it.fullString() }}"
}

object VoidFunctionType : Type() {
    override fun toString() = "fun"
}