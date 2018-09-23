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

    fun insertAt(index: Int, instruction: Instruction): Instruction {
        this.instructions.add(index, instruction)
        return instruction
    }

    fun append(instruction: Instruction): Instruction {
        this.instructions += instruction
        return instruction
    }

    fun headerString(): String = "<$name>"

    override fun toString(): String {
        return (instructions + terminator).joinToString(separator = "",
                prefix = "Block ${this.headerString()}\n") { "$it\n" }
    }
}

class Body {
    val blocks = mutableListOf<BasicBlock>()

    fun push(block: BasicBlock) {
        this.blocks += block
    }
}