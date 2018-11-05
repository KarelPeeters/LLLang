package language.ir

sealed class Instruction(val name: String?, type: Type) : Value(type) {
    private var _block: BasicBlock? = null

    val block get() = _block!!
    fun setBlock(block: BasicBlock?) {
        this._block = block
    }

    override fun str(env: NameEnv) = "%${env.value(this)} $type"

    abstract fun fullStr(env: NameEnv): String
}

class Alloc(name: String?, val inner: Type) : Instruction(name, inner.pointer) {
    override fun fullStr(env: NameEnv) = "${str(env)} = alloc $inner"
}

class Store(pointer: Value, value: Value) : Instruction(null, VoidType) {
    var pointer by operand(pointer)
    var value by operand(value)

    override fun fullStr(env: NameEnv) = "store ${value.str(env)} -> ${pointer.str(env)}"
}

class Load(name: String?, pointer: Value) : Instruction(name, pointer.type.unpoint!!) {
    var pointer by operand(pointer)

    override fun fullStr(env: NameEnv) = "${str(env)} = load ${pointer.str(env)}"
}

class BinaryOp(name: String?, val opType: BinaryOpType, left: Value, right: Value) :
        Instruction(name, opType.returnType(left.type, right.type)) {
    var left by operand(left)
    var right by operand(right)

    override fun fullStr(env: NameEnv) = "${str(env)} = $opType ${left.str(env)}, ${right.str(env)}"
}

class UnaryOp(name: String?, val opType: UnaryOpType, value: Value) :
        Instruction(name, value.type) {
    var value by operand(value)

    override fun fullStr(env: NameEnv) = "${str(env)} = not ${value.str(env)}"
}

class Phi(name: String?, type: Type) : Instruction(name, type) {
    private val _sources = mutableMapOf<BasicBlock, Value>()
    val sources: Map<BasicBlock, Value> get() = _sources

    fun set(block: BasicBlock, value: Value) {
        require(value.type == this.type) { "value type ${value.type} should match phi type ${this.type}" }

        val prev = _sources[block]
        _sources[block] = value

        value.users += this
        if (prev != null && prev !in _sources.values)
            prev.users -= this
    }

    fun remove(block: BasicBlock) {
        val prev = _sources.remove(block)
        if (prev != null && prev !in _sources.values)
            prev.users -= this
    }

    override fun delete() {
        sources.values.forEach { it.users -= this }
    }

    override fun replaceOperand(from: Value, to: Value) {
        _sources.replaceAll { _, v -> if (v == from) to else v }

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
