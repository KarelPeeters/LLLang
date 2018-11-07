package language.ir

/**
 * A list of instructions with a Terminator at the end. No control flow happens within a BasicBlock.
 */
class BasicBlock(val name: String?) : Value(BlockType) {
    override val replaceAble = false

    val instructions = mutableListOf<Instruction>()

    var terminator: Terminator
        get() = instructions.last() as Terminator
        set(value) {
            if (hasTerminator())
                instructions.removeAt(instructions.lastIndex)
            instructions.add(value)
            value.setBlock(this)
        }

    private fun hasTerminator() = instructions.lastOrNull() is Terminator

    private var _function: Function? = null
    val function get() = _function!!
    fun setFunction(block: Function?) {
        this._function = block
    }

    fun insertAt(index: Int, instruction: Instruction) {
        this.instructions.add(index, instruction)
        instruction.setBlock(this)
    }

    fun append(instruction: Instruction) {
        require(instruction !is Terminator)

        if (hasTerminator())
            this.instructions.add(instructions.lastIndex, instruction)
        else
            this.instructions.add(instruction)

        instruction.setBlock(this)
    }

    fun remove(instruction: Instruction) {
        require(instruction !is Terminator)

        require(this.instructions.remove(instruction))
        instruction.setBlock(null)
    }

    fun successors() = terminator.targets()
    fun predecessors() = this.users.mapNotNull { (it as? Terminator)?.block }

    override fun str(env: NameEnv) = "<${env.block(this)}>"

    fun fullStr(env: NameEnv) = instructions.joinToString(separator = "\n", prefix = "${str(env)}\n") { it.fullStr(env) }
}

object BlockType : Type() {
    override fun toString() = "block"
}