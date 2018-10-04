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