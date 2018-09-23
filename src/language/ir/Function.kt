package language.ir

/**
 * Represents a function with void return type and no parameters
 */
class Function {
    val blocks = mutableListOf<BasicBlock>()

    fun push(block: BasicBlock) {
        this.blocks += block
    }

    fun fullString() = blocks.joinToString("\n\n") { it.fullString() }
}