package language.ir

sealed class Instruction(val name: String, type: Type) : Value(type) {
    override fun toString() = "%$name $type"

    abstract fun fullString(): String
}

class Alloc(name: String, val inner: Type) : Instruction(name, inner.pointer) {
    override fun fullString() = "$this = alloc $inner"
}

class Store(pointer: Value, value: Value) : Instruction("", VoidType) {
    var pointer by operand(pointer)
    var value by operand(value)

    override fun fullString() = "store $value -> $pointer"
}

class Load(name: String, pointer: Value) : Instruction(name, pointer.type.unpoint!!) {
    var pointer by operand(pointer)

    override fun fullString() = "$this = load $pointer"
}

class BinaryOp(name: String, val opType: BinaryOpType, left: Value, right: Value) :
        Instruction(name, opType.returnType(left.type, right.type)) {
    var left by operand(left)
    var right by operand(right)

    override fun fullString() = "$this = $opType $left, $right"
}

class Phi(name: String, type: Type) : Instruction(name, type) {
    private val _sources = mutableMapOf<BasicBlock, Value>()
    val sources: Map<BasicBlock, Value> get() = _sources

    fun set(block: BasicBlock, value: Value) {
        val prev = _sources[block]
        _sources[block] = value

        block.users += this
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
        sources.keys.forEach { it.users -= this }
        sources.values.forEach { it.users -= this }
    }

    override fun replaceOperand(from: Value, to: Value) {
        if (from is BasicBlock) {
            //can only replace with other BasicBlock
            require(to is BasicBlock)
            _sources[from]?.let { fromValue ->
                val toValue = _sources[to]
                //only 'overwrite' an existing pair if the values are the same anyway
                if (toValue != null) require(toValue == fromValue)
                else {
                    _sources.remove(from)
                    _sources[to as BasicBlock] = fromValue
                }
            }
        }

        _sources.replaceAll { _, v -> if (v == from) to else v }

        from.users -= this
        to.users += this
    }

    override fun fullString() = "$this = phi [${sources.toList().joinToString(", ") { (k, v) -> "$k: $v" }}]"
}
