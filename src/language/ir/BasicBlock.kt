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

class Alloc(val name: String) : Value(PINT32) {
    override fun valueString() = "%$name $type"
    override fun toString() = "${valueString()} = alloc"
}

class BasicBlock(val name: String) {
    val allocs = mutableListOf<Alloc>()
    val instructions = mutableListOf<Instruction>()
    lateinit var terminator: Terminator

    fun append(alloc: Alloc) {
        this.allocs += alloc
    }

    fun append(instruction: Instruction) {
        this.instructions += instruction
    }

    fun headerString(): String = "<$name>"

    override fun toString(): String {
        return (allocs + instructions + terminator).joinToString(separator = "",
                prefix = "Block ${this.headerString()}\n") { "$it\n" }
    }
}

class Body {
    val blocks = mutableListOf<BasicBlock>()

    fun push(block: BasicBlock) {
        this.blocks += block
    }
}