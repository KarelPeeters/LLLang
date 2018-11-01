package language.ir

/**
 * A list of instructions with a Terminator at the end. No control flow happens within a BasicBlock.
 */
class BasicBlock(val name: String?) : Value(BlockType) {
    override val replaceAble = false

    val instructions = mutableListOf<Instruction>()

    private lateinit var _terminator: Terminator
    var terminator: Terminator
        get() = _terminator
        set(value) { value.block = this; _terminator = value }

    fun insertAt(index: Int, instruction: Instruction) {
        this.instructions.add(index, instruction)
        instruction.setBlock(this)
    }

    fun append(instruction: Instruction) {
        this.instructions += instruction
        instruction.setBlock(this)
    }

    fun remove(instruction: Instruction) {
        this.instructions.remove(instruction)
        instruction.setBlock(null)
    }

    fun successors() = terminator.targets()
    fun predecessors() = this.users.mapNotNull { (it as? Terminator)?.block }

    override fun str(env: NameEnv) = "<${env.block(this)}>"

    fun fullStr(env: NameEnv) = instructions.joinToString(
            separator = "", prefix = "${str(env)}\n", postfix = terminator.fullStr(env)
    ) { "${it.fullStr(env)}\n" }
}

object BlockType : Type() {
    override fun toString() = "block"
}