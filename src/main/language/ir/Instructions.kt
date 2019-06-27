package language.ir

import language.ir.IntegerType.Companion.bool
import language.ir.IntegerType.Companion.i32

sealed class Instruction(val name: String?, type: Type, val pure: Boolean) : Value(type), User by User() {
    private var _block: BasicBlock? = null

    val block get() = _block!!
    fun setBlock(block: BasicBlock?) {
        this._block = block
    }

    abstract fun verify()

    abstract fun clone(): Instruction

    override fun str(env: NameEnv) = "%${env.value(this)} $type"

    abstract fun fullStr(env: NameEnv): String
}

sealed class BasicInstruction(name: String?, type: Type, pure: Boolean) : Instruction(name, type, pure) {
    fun deleteFromBlock() {
        block.remove(this)
        delete()
    }

    fun indexInBlock() = block.basicInstructions.indexOf(this).also { require(it >= 0) }

    abstract override fun clone(): BasicInstruction
}

class Alloc(name: String?, val inner: Type) : BasicInstruction(name, inner.pointer, true) {
    override fun fullStr(env: NameEnv) = "${str(env)} = alloc $inner"

    override fun clone() = Alloc(name, inner)

    override fun verify() {}
}

class Store(pointer: Value, value: Value) : BasicInstruction(null, UnitType, false) {
    var pointer by operand(pointer)
    var value by operand(value)

    override fun clone() = Store(pointer, value)

    override fun verify() {
        check(pointer.type.unpoint == value.type) { "pointer type must be pointer to value type" }
    }

    override fun fullStr(env: NameEnv) = "store ${value.str(env)} -> ${pointer.str(env)}"
}

class Load(name: String?, pointer: Value) : BasicInstruction(name, pointer.type.unpoint!!, true) {
    var pointer by operand(pointer)

    override fun clone() = Load(name, pointer)

    override fun verify() {
        check(pointer.type.unpoint == this.type) { "pointer type must be pointer to load type" }
    }

    override fun fullStr(env: NameEnv) = "${str(env)} = load ${pointer.str(env)}"
}

class BinaryOp(name: String?, val opType: BinaryOpType, left: Value, right: Value) :
        BasicInstruction(name, opType.returnType(left.type, right.type), true) {
    var left by operand(left)
    var right by operand(right)

    override fun clone() = BinaryOp(name, opType, left, right)

    override fun verify() {
        check(opType.returnType(left.type, right.type) == this.type) { "left and right types must result in binaryOp type" }
    }

    override fun fullStr(env: NameEnv) = "${str(env)} = $opType ${left.str(env)}, ${right.str(env)}"
}

class UnaryOp(name: String?, val opType: UnaryOpType, value: Value) :
        BasicInstruction(name, value.type, true) {
    var value by operand(value)

    override fun clone() = UnaryOp(name, opType, value)

    override fun verify() {
        check(value.type == this.type) { "value type must be unaryOp type" }
    }

    override fun fullStr(env: NameEnv) = "${str(env)} = $opType ${value.str(env)}"
}

class Phi(name: String?, type: Type) : BasicInstruction(name, type, true) {
    val sources by operandMap<BasicBlock, Value>()

    override fun clone(): Phi {
        val new = Phi(name, type)
        for ((block, value) in this.sources)
            new.sources[block] = value
        return new
    }

    override fun verify() {
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

class Eat : BasicInstruction(null, UnitType, false) {
    val arguments by operandList<Value>()

    override fun clone(): Eat {
        val new = Eat()
        new.arguments.addAll(this.arguments)
        return new
    }

    override fun verify() {}

    override fun fullStr(env: NameEnv) = "eat " + arguments.joinToString { it.str(env) }
}

class Blur(value: Value) : BasicInstruction(null, value.type, false) {
    val value by operand(value)

    override fun clone() = Blur(value)

    override fun verify() {
        check(value.type == this.type) { "value type must be blur type" }
    }

    override fun fullStr(env: NameEnv) = "${str(env)} = blur ${value.str(env)}"
}

class Call(name: String?, target: Value, arguments: List<Value>)
    : BasicInstruction(name, (target.type as FunctionType).returnType, false) {
    var target by operand(target)
    val arguments by operandList(arguments)

    override fun clone() = Call(name, target, arguments.toList())

    override fun verify() {
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

sealed class GetSubValue(name: String?, type: Type)
    : BasicInstruction(name, type, true) {

    abstract val target: Value

    class GetStructValue(name: String?, target: Value, val index: Int)
        : GetSubValue(name, (target.type as StructType).properties[index]) {

        override val target by operand(target)

        override fun clone() = GetStructValue(name, target, index)

        override fun verify() {
            val structType = target.type
            check(structType is StructType) { "target is a struct" }
            check(index in structType.properties.indices) { "valid index" }
            check(structType.properties[index] == this.type) { "type match" }
        }

        override fun fullStr(env: NameEnv) = "${str(env)} = get ${target.str(env)} $index"
    }

    class GetArrayValue(name: String?, target: Value, index: Value)
        : GetSubValue(name, (target.type as ArrayType).inner) {

        override val target by operand(target)
        val index by operand(index)

        override fun clone() = GetArrayValue(name, target, index)

        override fun verify() {
            val structType = target.type
            check(structType is ArrayType) { "target is a struct" }
            check(index.type is IntegerType) { "valid index" }
            check(structType.inner == this.type) { "type match" }
        }

        override fun fullStr(env: NameEnv) = "${str(env)} = get ${target.str(env)} $index"
    }

    companion object {
        fun getFixedIndex(target: Value, index: Int) = when (target.type as AggregateType) {
            is StructType -> GetStructValue(null, target, index)
            is ArrayType -> GetArrayValue(null, target, Constant(i32, index))
        }
    }
}

sealed class GetSubPointer(name: String?, type: Type)
    : BasicInstruction(name, type, true) {
    abstract var target: Value

    class Array(name: String?, target: Value, index: Value)
        : GetSubPointer(name, (target.type.unpoint as ArrayType).inner.pointer) {

        override var target by operand(target)
        val index by operand(index)

        override fun clone() = Array(name, target, index)

        override fun verify() {
            val arrType = target.type.unpoint
            check(arrType is ArrayType) { "target is a struct pointer" }
            check(index.type is IntegerType) { "index is integer" }
            check(arrType.inner.pointer == this.type) { "type match" }
        }

        override fun fullStr(env: NameEnv) = "${str(env)} = aptr ${target.str(env)} ${index.str(env)}"
    }

    class Struct(name: String?, target: Value, val index: Int)
        : GetSubPointer(name, (target.type.unpoint as StructType).properties[index].pointer) {

        override var target by operand(target)

        override fun clone() = Struct(name, target, index)

        override fun verify() {
            val structType = target.type.unpoint
            check(structType is StructType) { "target is a struct pointer" }
            check(index in structType.properties.indices) { "valid index" }
            check(structType.properties[index].pointer == this.type) { "type match" }
        }

        override fun fullStr(env: NameEnv) = "${str(env)} = sptr ${target.str(env)} $index"
    }
}

class AggregateValue(name: String?, val atype: AggregateType, values: List<Value>)
    : BasicInstruction(name, atype, true) {
    val values by operandList(values)

    override fun clone() = AggregateValue(name, atype, values)

    override fun verify() {
        check(atype.size == values.size) { "sizes must match" }
        for ((t, v) in atype.innerTypes zip values)
            check(t == v.type) { "inner types must match" }
    }

    override fun fullStr(env: NameEnv): String {
        val prefix = str(env)
        val propStr = values.joinToString { it.str(env) }
        return when (atype) {
            is StructType -> "$prefix = ${atype.name}($propStr)"
            is ArrayType -> "$prefix = {$propStr}"
        }
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

    override fun verify() {
        check(value.type == bool) { "branch conditional must be a boolean" }
    }

    override fun targets() = setOf(ifTrue, ifFalse)
    override fun fullStr(env: NameEnv) = "branch ${value.str(env)} T ${ifTrue.str(env)} F ${ifFalse.str(env)}"
}

class Jump(target: BasicBlock?) : Terminator() {
    var target by operand(target)

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