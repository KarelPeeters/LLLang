package language.ir

/**
 * Represents a function with void return type and no parameters
 */
class Function : Value(VoidFunctionType) {
    lateinit var entry: BasicBlock

    val blocks = mutableListOf<BasicBlock>()

    fun append(block: BasicBlock) {
        if (this.blocks.isEmpty())
            entry = block

        this.blocks += block
    }

    override fun toString() = fullStr(NameEnv())

    fun fullStr(env: NameEnv) = "entry: ${entry.str(env)}\n${blocks.joinToString("\n\n") { it.fullStr(env) }}"
}

object VoidFunctionType : Type() {
    override fun toString() = "fun"
}