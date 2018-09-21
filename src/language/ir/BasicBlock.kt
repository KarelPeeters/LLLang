package language.ir

sealed class Terminator

class Branch(var value: Value, var ifTrue: BasicBlock, var ifFalse: BasicBlock) : Terminator() {
    override fun toString() = "branch ${value.valueString()} T ${ifTrue.headerString()} F ${ifFalse.headerString()}"
}
class Jump(var target: BasicBlock) : Terminator() {
    override fun toString() = "jump ${target.headerString()}"
}
object Exit : Terminator() {
    override fun toString() = "exit"
}

class BasicBlock(val name: String) {
    val instructions = mutableListOf<Instruction>()
    lateinit var terminator: Terminator

    fun push(instruction: Instruction) {
        this.instructions += instruction
    }

    fun headerString(): String = "<$name>"

    override fun toString(): String {
        return this.instructions.joinToString(separator = "",
                prefix = "Block ${this.headerString()}\n",
                postfix = "$terminator") { "$it\n" }
    }
}

class Body {
    val blocks = mutableListOf<BasicBlock>()

    fun push(block: BasicBlock) {
        this.blocks += block
    }
}