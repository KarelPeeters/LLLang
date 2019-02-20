package language.ir

import language.ir.IntegerType.Companion.bool
import language.util.replace
import language.util.replaceValues

sealed class Instruction constructor(val name: String?, type: Type, val pure: Boolean) : Value(type) {
    private var _block: BasicBlock? = null

    val block get() = _block!!
    fun setBlock(block: BasicBlock?) {
        this._block = block
    }

    fun deleteFromBlock() {
        block.remove(this)
        shallowDelete()
    }

    abstract fun clone(): Instruction

    override fun str(env: NameEnv) = "%${env.value(this)} $type"

    abstract fun fullStr(env: NameEnv): String
}

class Alloc(name: String?, val inner: Type) : Instruction(name, inner.pointer, true) {
    override fun fullStr(env: NameEnv) = "${str(env)} = alloc $inner"

    override fun clone() = Alloc(name, inner)

    override fun verify() {}
}

class Store(pointer: Value, value: Value) : Instruction(null, UnitType, false) {
    var pointer by operand(pointer)
    var value by operand(value)

    override fun clone() = Store(pointer, value)

    override fun verify() {
        require(pointer.type.unpoint == value.type) { "pointer type must be pointer to value type" }
    }

    override fun fullStr(env: NameEnv) = "store ${value.str(env)} -> ${pointer.str(env)}"
}

class Load(name: String?, pointer: Value) : Instruction(name, pointer.type.unpoint!!, true) {
    var pointer by operand(pointer)

    override fun clone() = Load(name, pointer)

    override fun verify() {
        require(pointer.type.unpoint == this.type) { "pointer type must be pointer to load type" }
    }

    override fun fullStr(env: NameEnv) = "${str(env)} = load ${pointer.str(env)}"
}

class BinaryOp(name: String?, val opType: BinaryOpType, left: Value, right: Value) :
        Instruction(name, opType.returnType(left.type, right.type), true) {
    var left by operand(left)
    var right by operand(right)

    override fun clone() = BinaryOp(name, opType, left, right)

    override fun verify() {
        require(opType.returnType(left.type, right.type) == this.type) { "left and right types must result in binaryOp type" }
    }

    override fun fullStr(env: NameEnv) = "${str(env)} = $opType ${left.str(env)}, ${right.str(env)}"
}

class UnaryOp(name: String?, val opType: UnaryOpType, value: Value) :
        Instruction(name, value.type, true) {
    var value by operand(value)

    override fun clone() = UnaryOp(name, opType, value)

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

    override fun clone(): Phi {
        val new = Phi(name, type)
        for ((block, value) in this.sources)
            new.set(block, value)
        return new
    }

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

    override fun shallowDelete() {
        super.shallowDelete()
        (sources.keys + sources.values).forEach { it.users -= this }
    }

    override fun replaceOperand(from: Value, to: Value) {
        var changed = _sources.replaceValues(from, to)

        if (from is BasicBlock) {
            require(to is BasicBlock)

            if (_sources.containsKey(from)) {
                //if to is already a source assert they're the same value
                if (_sources.containsKey(to))
                    require(_sources[to] == sources[from])

                _sources[to] = _sources.getValue(from)
                _sources.remove(from)
                changed = true
            }
        }

        if (changed) {
            from.users -= this
            to.users += this
        }
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

    override fun clone() = Eat()

    override fun verify() {}

    fun addOperand(value: Value) {
        _operands += value
        value.users += this
    }

    fun addOperands(values: List<Value>) {
        _operands.addAll(values)
        for (value in values)
            value.users += this
    }

    fun removeOperand(value: Value) {
        _operands.remove(value)
        if (value !in _operands)
            value.users -= this
    }

    override fun shallowDelete() {
        super.shallowDelete()
        operands.forEach { it.users -= this }
        _operands.clear()
    }

    override fun replaceOperand(from: Value, to: Value) {
        super.replaceOperand(from, to)
        if (_operands.replace(from, to)) {
            from.users -= this
            to.users += this
        }
    }

    override fun fullStr(env: NameEnv) = "eat " + operands.joinToString { it.str(env) }
}

class Blur(value: Value) : Instruction(null, value.type, false) {
    val value by operand(value)

    override fun clone() = Blur(value)

    override fun verify() {
        require(value.type == this.type) { "value type must be blur type" }
    }

    override fun fullStr(env: NameEnv) = "${str(env)} = blur ${value.str(env)}"
}

class Call(name: String?, target: Value, arguments: List<Value>)
    : Instruction(name, (target.type as FunctionType).returnType, false) {
    val target by operand(target)
    private val _arguments = arguments.toMutableList()
    val arguments: List<Value> = _arguments

    init {
        for (arg in arguments) {
            arg.users += this
        }
    }

    override fun clone() = Call(name, target, arguments.toList())

    override fun verify() {
        val funcType = target.type
        require(funcType is FunctionType) { "target must be a function" }
        require(funcType.paramTypes.size == arguments.size) { "parameter sizes must match" }
        require(funcType.paramTypes.zip(arguments).all { (x, y) -> x == y.type }) { "parameter types must match" }
        require(funcType.returnType == this.type) { "return type must match" }
    }

    fun setArgument(i: Int, value: Value) {
        val prev = _arguments.set(i, value)
        value.users += this
        if (prev !in _arguments)
            prev.users -= this
    }

    override fun replaceOperand(from: Value, to: Value) {
        super.replaceOperand(from, to)
        if (_arguments.replace(from, to)) {
            from.users -= this
            to.users += this
        }
    }

    override fun shallowDelete() {
        super.shallowDelete()
        for (arg in _arguments)
            arg.users -= this
        _arguments.clear()
    }

    override val operands: List<Value>
        get() = super.operands + arguments

    override fun fullStr(env: NameEnv): String {
        return "${str(env)} = call ${target.str(env)}(${_arguments.joinToString { it.str(env) }})"
    }
}

sealed class Terminator : Instruction(null, UnitType, false) {
    abstract fun targets(): Set<BasicBlock>
}

class Branch(value: Value, ifTrue: BasicBlock, ifFalse: BasicBlock) : Terminator() {
    var value by operand(value)
    var ifTrue by operand<BasicBlock>(ifTrue)
    var ifFalse by operand<BasicBlock>(ifFalse)

    override fun clone() = Branch(value, ifTrue, ifFalse)

    override fun verify() {
        require(value.type == bool) { "branch conditional must be a boolean" }
    }

    override fun targets() = setOf(ifTrue, ifFalse)
    override fun fullStr(env: NameEnv) = "branch ${value.str(env)} T ${ifTrue.str(env)} F ${ifFalse.str(env)}"
}

class Jump(target: BasicBlock?) : Terminator() {
    var target by operand<BasicBlock>(target)

    override fun clone() = Jump(target)

    override fun verify() {}

    override fun targets() = setOf(target)
    override fun fullStr(env: NameEnv) = "jump ${target.str(env)}"
}

class Exit : Terminator() {
    override fun clone() = Exit()

    override fun verify() {}

    override fun targets() = setOf<BasicBlock>()
    override fun fullStr(env: NameEnv) = "exit"
}

class Return(value: Value) : Terminator() {
    var value by operand(value)

    override fun clone() = Return(value)

    override fun verify() {}

    override fun targets() = emptySet<BasicBlock>()
    override fun fullStr(env: NameEnv) = "return ${value.str(env)}"
}