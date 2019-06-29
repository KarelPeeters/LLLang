package language.ir

import language.util.takeWhileIsInstance

/**
 * A list of [BasicInstruction]s with a [Terminator] at the end. No control flow happens within a BasicBlock.
 */
class BasicBlock(val name: String?) : Value(BlockType) {
    val basicInstructions = mutableListOf<BasicInstruction>()
    val instructions get() = basicInstructions + terminator

    private var _terminator: Terminator? = null
    var terminator: Terminator
        get() = _terminator!!
        set(value) = setTerminator(value)

    @JvmName("_setTerminator")
    fun setTerminator(new: Terminator?) {
        new?.setBlock(this)
        _terminator = new
    }

    private var _function: Function? = null
    val function get() = _function!!
    fun setFunction(function: Function?) {
        this._function = function
    }

    fun indexInFunction() = function.blocks.indexOf(this)
            .also { require(it >= 0) }

    fun appendOrReplaceTerminator(instruction: Instruction) {
        when (instruction) {
            is BasicInstruction -> append(instruction)
            is Terminator -> setTerminator(instruction)
        }.also { }
    }

    fun append(instruction: BasicInstruction) = add(basicInstructions.lastIndex + 1, instruction)

    fun add(index: Int, instruction: BasicInstruction) {
        require(instruction !is Terminator)
        basicInstructions.add(index, instruction)
        instruction.setBlock(this)
    }

    fun addAll(instructions: List<BasicInstruction>) = addAll(basicInstructions.lastIndex + 1, instructions)

    fun addAll(index: Int, instructions: List<BasicInstruction>) {
        for (instr in instructions) {
            check(instr !is Terminator)
            instr.setBlock(this)
        }

        this.basicInstructions.addAll(index, instructions)
    }

    fun remove(instruction: BasicInstruction) {
        require(this.basicInstructions.remove(instruction))
        instruction.setBlock(null)
    }

    fun deepDelete() {
        instructions.forEach { it.delete() }
        delete()
    }

    fun phis() = instructions.takeWhileIsInstance<Phi>()

    fun successors() = terminator.targets()
    fun predecessors() = this.users.mapNotNull { (it as? Terminator)?.block }

    fun str(env: NameEnv) = untypedStr(env)

    override fun untypedStr(env: NameEnv) = "<${env.block(this)}>"

    fun fullStr(env: NameEnv) = instructions.joinToString(separator = "\n", prefix = "  ${str(env, false)}\n") { "    " + it.fullStr(env) }
}

object BlockType : Type() {
    override fun toString() = "block"
}