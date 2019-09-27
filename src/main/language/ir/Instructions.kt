package language.ir

import language.ir.IntegerType.Companion.bool
import language.ir.IntegerType.Companion.i32

sealed class Instruction(val name: String?, type: Type, val pure: Boolean) : Value(type) {
    private var _block: BasicBlock? = null

    val block get() = _block!!
    fun setBlock(block: BasicBlock?) {
        this._block = block
    }

    val function get() = block.function

    abstract fun typeCheck()

    abstract fun clone(): Instruction

    override fun untypedStr(env: NameEnv) = "%${env.value(this)}"

    abstract fun fullStr(env: NameEnv): String

    abstract fun matches(other: Instruction, maps: ValueMapper): Boolean
}

interface ValueMapper {
    operator fun invoke(left: Value, right: Value): Boolean
    operator fun invoke(left: List<Value>, right: List<Value>): Boolean
    operator fun invoke(left: Set<Value>, right: Set<Value>): Boolean
    operator fun invoke(left: Map<out Value, Value>, right: Map<out Value, Value>): Boolean
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

    override fun typeCheck() {
        check(inner.pointer == type) { "result type must be pointer to inner type" }
    }

    override fun matches(other: Instruction, maps: ValueMapper) =
            other is Alloc && inner == other.inner
}

class Store(pointer: Value, value: Value) : BasicInstruction(null, VoidType, false) {
    var pointer by operand(pointer)
    var value by operand(value)

    override fun clone() = Store(pointer, value)

    override fun typeCheck() {
        check(pointer.type.unpoint == value.type) { "pointer type must be pointer to value type" }
    }

    override fun fullStr(env: NameEnv) = "store ${pointer.str(env)} = ${value.str(env)}"

    override fun matches(other: Instruction, maps: ValueMapper) =
            other is Store && maps(pointer, other.pointer) && maps(value, other.value)
}

class Load(name: String?, pointer: Value) : BasicInstruction(name, pointer.type.unpoint!!, true) {
    var pointer by operand(pointer)

    override fun clone() = Load(name, pointer)

    override fun typeCheck() {
        check(pointer.type.unpoint == this.type) { "pointer type must be pointer to load type" }
    }

    override fun fullStr(env: NameEnv) = "${str(env)} = load ${pointer.str(env)}"

    override fun matches(other: Instruction, maps: ValueMapper) =
            other is Load && maps(pointer, other.pointer)
}

class BinaryOp(name: String?, val opType: BinaryOpType, left: Value, right: Value) :
        BasicInstruction(name, opType.returnType(left.type, right.type), true) {
    var left by operand(left)
    var right by operand(right)

    override fun clone() = BinaryOp(name, opType, left, right)

    override fun typeCheck() {
        check(opType.returnType(left.type, right.type) == this.type) { "left and right types must result in binaryOp type" }
    }

    override fun fullStr(env: NameEnv) = "${str(env)} = $opType ${left.str(env)}, ${right.str(env)}"

    override fun matches(other: Instruction, maps: ValueMapper) =
            other is BinaryOp && opType == other.opType && maps(left, other.left) && maps(right, other.right)
}

class UnaryOp(name: String?, val opType: UnaryOpType, value: Value) :
        BasicInstruction(name, value.type, true) {
    var value by operand(value)

    override fun clone() = UnaryOp(name, opType, value)

    override fun typeCheck() {
        check(value.type == this.type) { "value type must be unaryOp type" }
    }

    override fun fullStr(env: NameEnv) = "${str(env)} = $opType ${value.str(env)}"

    override fun matches(other: Instruction, maps: ValueMapper) =
            other is UnaryOp && opType == other.opType && maps(value, other.value)
}

class Phi(name: String?, type: Type) : BasicInstruction(name, type, true) {
    val sources by operandMap<BasicBlock, Value>()

    override fun clone(): Phi {
        val new = Phi(name, type)
        for ((block, value) in this.sources)
            new.sources[block] = value
        return new
    }

    override fun typeCheck() {
        check(sources.keys == block.predecessors().toSet()) { "phi sources don't match predecessors" }
        check(sources.values.all { it.type == this.type }) { "source types must all equal phi type" }
    }

    override fun fullStr(env: NameEnv): String {
        val labels = sources.toList().joinToString(", ") { (k, v) ->
            "${k.str(env)}: ${v.str(env)}"
        }
        return "${str(env)} = phi [$labels]"
    }

    override fun matches(other: Instruction, maps: ValueMapper) =
            other is Phi && maps(sources, other.sources)
}

class Eat(arguments: List<Value>) : BasicInstruction(null, VoidType, false) {
    val arguments by operandList(arguments)

    override fun clone(): Eat = Eat(arguments)

    override fun typeCheck() {}

    override fun fullStr(env: NameEnv) = "eat(" + arguments.joinToString { it.str(env) } + ")"

    override fun matches(other: Instruction, maps: ValueMapper) =
            other is Eat && maps(arguments, other.arguments)
}

class Blur(name: String?, value: Value) : BasicInstruction(name, value.type, false) {
    val value by operand(value)

    override fun clone() = Blur(name, value)

    override fun typeCheck() {
        check(value.type == this.type) { "value type must be blur type" }
    }

    override fun fullStr(env: NameEnv) = "${str(env)} = blur ${value.str(env)}"

    override fun matches(other: Instruction, maps: ValueMapper) =
            other is Blur && maps(value, other.value)
}

class Call(name: String?, target: Value, arguments: List<Value>)
    : BasicInstruction(name, (target.type as FunctionType).returnType, false) {
    var target by operand(target)
    val arguments by operandList(arguments)

    override fun clone() = Call(name, target, arguments.toList())

    override fun typeCheck() {
        val funcType = target.type
        check(funcType is FunctionType) { "target must be a function" }
        check(funcType.paramTypes.size == arguments.size) { "parameter sizes must match" }
        check(funcType.paramTypes.zip(arguments).all { (x, y) -> x == y.type }) { "parameter types must match" }
        check(funcType.returnType == this.type) { "return type must match" }
    }

    override fun fullStr(env: NameEnv): String {
        val retStr = if (type == VoidType) "" else "${str(env)} = "
        val targetStr = target.str(env, false)
        val argStr = arguments.joinToString { it.str(env) }
        return "${retStr}call $targetStr($argStr)"
    }

    override fun matches(other: Instruction, maps: ValueMapper) =
            other is Call && maps(target, other.target) && maps(arguments, other.arguments)
}

sealed class GetSubValue(name: String?, type: Type)
    : BasicInstruction(name, type, true) {

    abstract val target: Value

    class Struct(name: String?, target: Value, val index: Int)
        : GetSubValue(name, (target.type as StructType).properties[index]) {

        override val target by operand(target)

        override fun clone() = Struct(name, target, index)

        override fun typeCheck() {
            val structType = target.type
            check(structType is StructType) { "target is a struct" }
            check(index in structType.properties.indices) { "valid index" }
            check(structType.properties[index] == this.type) { "type match" }
        }

        override fun fullStr(env: NameEnv) = "${str(env)} = sget ${target.str(env)}, $index"

        override fun matches(other: Instruction, maps: ValueMapper) =
                other is Struct && maps(target, other.target) && index == other.index
    }

    class Array(name: String?, target: Value, index: Value)
        : GetSubValue(name, (target.type as ArrayType).inner) {

        override val target by operand(target)
        val index by operand(index)

        override fun clone() = Array(name, target, index)

        override fun typeCheck() {
            val structType = target.type
            check(structType is ArrayType) { "target is a struct" }
            check(index.type is IntegerType) { "valid index" }
            check(structType.inner == this.type) { "type match" }
        }

        override fun fullStr(env: NameEnv) = "${str(env)} = aget ${target.str(env)}, $index"

        override fun matches(other: Instruction, maps: ValueMapper) =
                other is Array && maps(target, other.target) && maps(index, other.index)
    }

    companion object {
        fun getFixedIndex(target: Value, index: Int) = when (target.type as AggregateType) {
            is StructType -> Struct(null, target, index)
            is ArrayType -> Array(null, target, Constant(i32, index))
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

        override fun typeCheck() {
            val arrType = target.type.unpoint
            check(arrType is ArrayType) { "target is a struct pointer" }
            check(index.type is IntegerType) { "index is integer" }
            check(arrType.inner.pointer == this.type) { "type match" }
        }

        override fun fullStr(env: NameEnv) = "${str(env)} = aptr ${target.str(env)}, ${index.str(env)}"

        override fun matches(other: Instruction, maps: ValueMapper) =
                other is Array && maps(target, other.target) && maps(index, other.index)
    }

    class Struct(name: String?, target: Value, val index: Int)
        : GetSubPointer(name, (target.type.unpoint as StructType).properties[index].pointer) {

        override var target by operand(target)

        override fun clone() = Struct(name, target, index)

        override fun typeCheck() {
            val structType = target.type.unpoint
            check(structType is StructType) { "target is a struct pointer" }
            check(index in structType.properties.indices) { "valid index" }
            check(structType.properties[index].pointer == this.type) { "type match" }
        }

        override fun fullStr(env: NameEnv) = "${str(env)} = sptr ${target.str(env)}, $index"

        override fun matches(other: Instruction, maps: ValueMapper) =
                other is Struct && maps(target, other.target) && index == other.index
    }
}

class AggregateValue(name: String?, type: AggregateType, values: List<Value>)
    : BasicInstruction(name, type, true) {
    val values by operandList(values)

    override fun clone() = AggregateValue(name, type as AggregateType, values)

    override fun typeCheck() {
        check(type is AggregateType)
        check(type.size == values.size) { "sizes must match" }
        for ((t, v) in type.innerTypes zip values)
            check(t == v.type) { "inner types must match" }
    }

    override fun fullStr(env: NameEnv): String {
        val prefix = str(env)
        val propStr = values.joinToString { it.str(env) }
        type as AggregateType
        return when (type) {
            is StructType -> "$prefix = $type($propStr)"
            is ArrayType -> "$prefix = {$propStr}"
        }
    }

    override fun matches(other: Instruction, maps: ValueMapper) =
            other is AggregateValue && maps(values, other.values)
}

sealed class Terminator : Instruction(null, VoidType, false) {
    abstract fun targets(): List<BasicBlock>

    abstract override fun clone(): Terminator
}

class Branch(value: Value, ifTrue: BasicBlock, ifFalse: BasicBlock) : Terminator() {
    var value by operand(value)
    var ifTrue by operand(ifTrue)
    var ifFalse by operand(ifFalse)

    override fun clone() = Branch(value, ifTrue, ifFalse)

    override fun typeCheck() {
        check(value.type == bool) { "branch conditional must be a boolean" }
    }

    override fun targets() = listOf(ifTrue, ifFalse)
    override fun fullStr(env: NameEnv) = "branch ${value.str(env)} ${ifTrue.str(env)} ${ifFalse.str(env)}"

    override fun matches(other: Instruction, maps: ValueMapper) =
            other is Branch && maps(value, other.value) && maps(ifTrue, other.ifTrue) && maps(ifFalse, other.ifFalse)
}

class Jump(target: BasicBlock?) : Terminator() {
    var target by operand(target)

    override fun clone() = Jump(target)

    override fun typeCheck() {}

    override fun targets() = listOf(target)
    override fun fullStr(env: NameEnv) = "jump ${target.str(env)}"

    override fun matches(other: Instruction, maps: ValueMapper) =
            other is Jump && maps(target, other.target)
}

class Exit : Terminator() {
    override fun clone() = Exit()

    override fun typeCheck() {}

    override fun targets() = emptyList<BasicBlock>()

    override fun fullStr(env: NameEnv) = "exit"

    override fun matches(other: Instruction, maps: ValueMapper) =
            other is Exit
}

class Return(value: Value) : Terminator() {
    var value by operand(value)

    override fun clone() = Return(value)

    override fun typeCheck() {}

    override fun targets() = emptyList<BasicBlock>()
    override fun fullStr(env: NameEnv) = when (value) {
        is VoidValue -> "return"
        else -> "return ${value.str(env)}"
    }

    override fun matches(other: Instruction, maps: ValueMapper) =
            other is Return && maps(value, other.value)
}