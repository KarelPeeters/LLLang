package language.ir

/**
 * Represents a function with void return type and no parameters
 */
class Function : Value(VoidFunctionType) {
    var entry by operand<BasicBlock>(null)

    val blocks = mutableListOf<BasicBlock>()

    fun append(block: BasicBlock) {
        this.blocks += block
        block.setFunction(this)
    }

    fun remove(block: BasicBlock) {
        require(this.blocks.remove(block))
        block.setFunction(null)
    }

    override fun toString() = fullStr(NameEnv())

    fun fullStr(env: NameEnv): String {
        blocks.forEach { env.block(it) } //preset names to keep them ordered
        return "entry: ${entry.str(env)}\n${blocks.joinToString("\n\n") { it.fullStr(env) }}"
    }
}

object VoidFunctionType : Type() {
    override fun toString() = "fun"
}