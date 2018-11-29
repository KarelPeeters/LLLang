package language.ir

import language.ir.IntegerType.Companion.bool

sealed class Instruction constructor(val name: String?, type: Type, val pure: Boolean) : Value(type) {
    private var _block: BasicBlock? = null

    val block get() = _block!!
    fun setBlock(block: BasicBlock?) {
        this._block = block
    }

    fun deleteFromBlock() {
        this.delete()
        block.remove(this)
    }

    override fun str(env: NameEnv) = "%${env.value(this)} $type"

    abstract fun fullStr(env: NameEnv): String
}

class Alloc(name: String?, val inner: Type) : Instruction(name, inner.pointer, true) {
    override fun fullStr(env: NameEnv) = "${str(env)} = alloc $inner"

    override fun verify() {}
}

class Store(pointer: Value, value: Value) : Instruction(null, UnitType, false) {
    var pointer by operand(pointer)
    var value by operand(value)

    override fun verify() {
        require(pointer.type.unpoint == value.type) { "pointer type must be pointer to value type" }
    }

    override fun fullStr(env: NameEnv) = "store ${value.str(env)} -> ${pointer.str(env)}"
}

class Load(name: String?, pointer: Value) : Instruction(name, pointer.type.unpoint!!, true) {
    var pointer by operand(pointer)

    override fun verify() {
        require(pointer.type.unpoint == this.type) { "pointer type must be pointer to load type" }
    }

    override fun fullStr(env: NameEnv) = "${str(env)} = load ${pointer.str(env)}"
}

class BinaryOp(name: String?, val opType: BinaryOpType, left: Value, right: Value) :
        Instruction(name, opType.returnType(left.type, right.type), true) {
    var left by operand(left)
    var right by operand(right)

    override fun verify() {
        require(opType.returnType(left.type, right.type) == this.type) { "left and right types must result in binaryOp type" }
    }

    override fun fullStr(env: NameEnv) = "${str(env)} = $opType ${left.str(env)}, ${right.str(env)}"
}

class UnaryOp(name: String?, val opType: UnaryOpType, value: Value) :
        Instruction(name, value.type, true) {
    var value by operand(value)

    override fun verify() {
        require(value.type == this.type) { "value type must be unaryOp type" }
    }

    override fun fullStr(env: NameEnv) = "${str(env)} = $opType ${value.str(env)}"
}

class Phi(name: String?, type: Type) : Instruction(name, type, true) {
    private val _sources = mutableMapOf<BasicBlock, Value>()
    val sources: Map<BasicBlock, Value> get() = _sources

    override val operands: List<Value>
        get() = sources.values.toList()

    override fun verify() {
        require(sources.keys == block.predecessors().toSet()) { "must have source for every block predecessor" }
        require(sources.values.all { it.type == this.type }) { "source types must all equal phi type" }
    }

    fun set(block: BasicBlock, value: Value) {
        val prev = _sources[block]
        _sources[block] = value

        block.users += this
        value.users += this
        if (prev != null && prev !in _sources.values)
            prev.users -= this
    }

    fun remove(block: BasicBlock) {
        block.users -= this
        val prev = _sources.remove(block)
        if (prev != null && prev !in _sources.values)
            prev.users -= this
    }

    override fun delete() {
        (sources.keys + sources.values).forEach { it.users -= this }
    }

    override fun replaceOperand(from: Value, to: Value) {
        _sources.replaceAll { _, v -> if (v == from) to else v }

        if (from is BasicBlock && to is BasicBlock && _sources.containsKey(from)) {
            if (_sources.containsKey(to)) {
                require(_sources[to] == sources[from])
            }

            _sources[to] = _sources.getValue(from)
            _sources.remove(from)
        }

        from.users -= this
        to.users += this
    }

    override fun fullStr(env: NameEnv): String {
        val labels = sources.toList().joinToString(", ") { (k, v) ->
            "${k.str(env)}: ${v.str(env)}"
        }
        return "${str(env)} = phi [$labels]"
    }
}

class Eat : Instruction(null, UnitType, false) {
    private val _operands = mutableListOf<Value>()
    override val operands: List<Value> = _operands

    override fun verify() {}

    fun addOperand(value: Value) {
        if (value !in _operands) {
            _operands += value
            value.users += this
        }
    }

    fun removeOperand(value: Value) {
        _operands.remove(value)
        value.users -= this
    }

    override fun delete() {
        operands.forEach { it.users -= this }
        _operands.clear()
    }

    override fun replaceOperand(from: Value, to: Value) {
        _operands.replaceAll { if (it == from) to else it }
        from.users -= this
        to.users += this
    }

    override fun fullStr(env: NameEnv) = "eat " + operands.joinToString { it.str(env) }
}

class Blur(value: Value) : Instruction(null, value.type, false) {
    val value by operand(value)

    override fun verify() {
        require(value.type == this.type) { "value type must be blur type" }
    }

    override fun fullStr(env: NameEnv) = "${str(env)} = blur ${value.str(env)}"
}

sealed class Terminator : Instruction(null, VoidType, false) {
    abstract fun targets(): Set<BasicBlock>
}

class Branch(value: Value, ifTrue: BasicBlock, ifFalse: BasicBlock) : Terminator() {
    var value by operand(value)
    var ifTrue by operand<BasicBlock>(ifTrue)
    var ifFalse by operand<BasicBlock>(ifFalse)

    override fun verify() {
        require(value.type == bool) { "branch conditional must be a boolean" }
    }

    override fun targets() = setOf(ifTrue, ifFalse)
    override fun fullStr(env: NameEnv) = "branch ${value.str(env)} T ${ifTrue.str(env)} F ${ifFalse.str(env)}"
}

class Jump(target: BasicBlock?) : Terminator() {
    var target by operand<BasicBlock>(target)

    override fun verify() {}

    override fun targets() = setOf(target)
    override fun fullStr(env: NameEnv) = "jump ${target.str(env)}"
}

object Exit : Terminator() {
    override fun verify() {}

    override fun targets() = setOf<BasicBlock>()
    override fun fullStr(env: NameEnv) = "exit"
}

class Return(value: Value) : Terminator() {
    var value by operand(value)

    override fun verify() {}

    override fun targets() = emptySet<BasicBlock>()
    override fun fullStr(env: NameEnv) = "return ${value.str(env)}"
}