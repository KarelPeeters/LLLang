package language.ir

import language.ir.IntegerType.Companion.bool

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

    override fun doVerify() {}
}

class Store(pointer: Value, value: Value) : Instruction(null, UnitType, false) {
    var pointer by operand(pointer)
    var value by operand(value)

    override fun clone() = Store(pointer, value)

    override fun doVerify() {
        check(pointer.type.unpoint == value.type) { "pointer type must be pointer to value type" }
    }

    override fun fullStr(env: NameEnv) = "store ${value.str(env)} -> ${pointer.str(env)}"
}

class Load(name: String?, pointer: Value) : Instruction(name, pointer.type.unpoint!!, true) {
    var pointer by operand(pointer)

    override fun clone() = Load(name, pointer)

    override fun doVerify() {
        check(pointer.type.unpoint == this.type) { "pointer type must be pointer to load type" }
    }

    override fun fullStr(env: NameEnv) = "${str(env)} = load ${pointer.str(env)}"
}

class BinaryOp(name: String?, val opType: BinaryOpType, left: Value, right: Value) :
        Instruction(name, opType.returnType(left.type, right.type), true) {
    var left by operand(left)
    var right by operand(right)

    override fun clone() = BinaryOp(name, opType, left, right)

    override fun doVerify() {
        check(opType.returnType(left.type, right.type) == this.type) { "left and right types must result in binaryOp type" }
    }

    override fun fullStr(env: NameEnv) = "${str(env)} = $opType ${left.str(env)}, ${right.str(env)}"
}

class UnaryOp(name: String?, val opType: UnaryOpType, value: Value) :
        Instruction(name, value.type, true) {
    var value by operand(value)

    override fun clone() = UnaryOp(name, opType, value)

    override fun doVerify() {
        check(value.type == this.type) { "value type must be unaryOp type" }
    }

    override fun fullStr(env: NameEnv) = "${str(env)} = $opType ${value.str(env)}"
}

class Phi(name: String?, type: Type) : Instruction(name, type, true) {
    val sources = operandMap<BasicBlock, Value>()

    override fun clone(): Phi {
        val new = Phi(name, type)
        for ((block, value) in this.sources)
            new.sources[block] = value
        return new
    }

    override fun doVerify() {
        check(sources.keys == block.predecessors().toSet()) { "must have source for every block predecessor" }
        check(sources.values.all { it.type == this.type }) { "source types must all equal phi type" }
    }

    override fun fullStr(env: NameEnv): String {
        val labels = sources.toList().joinToString(", ") { (k, v) ->
            "${k.str(env)}: ${v.str(env)}"
        }
        return "${str(env)} = phi [$labels]"
    }
}

class Eat : Instruction(null, UnitType, false) {
    val arguments = operandList<Value>()

    override fun clone(): Eat {
        val new = Eat()
        new.arguments.addAll(this.arguments)
        return new
    }

    override fun doVerify() {}

    override fun fullStr(env: NameEnv) = "eat " + arguments.joinToString { it.str(env) }
}

class Blur(value: Value) : Instruction(null, value.type, false) {
    val value by operand(value)

    override fun clone() = Blur(value)

    override fun doVerify() {
        check(value.type == this.type) { "value type must be blur type" }
    }

    override fun fullStr(env: NameEnv) = "${str(env)} = blur ${value.str(env)}"
}

class Call(name: String?, target: Value, arguments: List<Value>)
    : Instruction(name, (target.type as FunctionType).returnType, false) {
    val target by operand(target)
    val arguments = operandList(arguments)

    override fun clone() = Call(name, target, arguments.toList())

    override fun doVerify() {
        val funcType = target.type
        check(funcType is FunctionType) { "target must be a function" }
        check(funcType.paramTypes.size == arguments.size) { "parameter sizes must match" }
        check(funcType.paramTypes.zip(arguments).all { (x, y) -> x == y.type }) { "parameter types must match" }
        check(funcType.returnType == this.type) { "return type must match" }
    }

    override fun fullStr(env: NameEnv): String {
        return "${str(env)} = call ${target.str(env)}(${arguments.joinToString { it.str(env) }})"
    }
}

sealed class Terminator : Instruction(null, UnitType, false) {
    abstract fun targets(): Set<BasicBlock>
}

class Branch(value: Value, ifTrue: BasicBlock, ifFalse: BasicBlock) : Terminator() {
    var value by operand(value)
    var ifTrue by operand(ifTrue)
    var ifFalse by operand(ifFalse)

    override fun clone() = Branch(value, ifTrue, ifFalse)

    override fun doVerify() {
        check(value.type == bool) { "branch conditional must be a boolean" }
    }

    override fun targets() = setOf(ifTrue, ifFalse)
    override fun fullStr(env: NameEnv) = "branch ${value.str(env)} T ${ifTrue.str(env)} F ${ifFalse.str(env)}"
}

class Jump(target: BasicBlock?) : Terminator() {
    var target by operand(target)

    override fun clone() = Jump(target)

    override fun doVerify() {}

    override fun targets() = setOf(target)
    override fun fullStr(env: NameEnv) = "jump ${target.str(env)}"
}

class Exit : Terminator() {
    override fun clone() = Exit()

    override fun doVerify() {}

    override fun targets() = setOf<BasicBlock>()
    override fun fullStr(env: NameEnv) = "exit"
}

class Return(value: Value) : Terminator() {
    var value by operand(value)

    override fun clone() = Return(value)

    override fun doVerify() {}

    override fun targets() = emptySet<BasicBlock>()
    override fun fullStr(env: NameEnv) = "return ${value.str(env)}"
}