package language.ir

sealed class Instruction(val name: String, type: Type, operandCount: Int) : Value(type, operandCount) {
    override fun toString() = "%$name $type"

    abstract fun fullString(): String
}

class Alloc(name: String, val inner: Type) : Instruction(name, inner.pointer, 0) {
    override fun fullString() = "$this = alloc $inner"
}

class Store(pointer: Value, value: Value) : Instruction("", VoidType, 2) {
    var pointer by operand(0, pointer)
    var value by operand(1, value)

    override fun fullString() = "store $value -> $pointer"
}

class Load(name: String, pointer: Value) : Instruction(name, pointer.type.unpoint!!, 1) {
    var pointer by operand(0, pointer)

    override fun fullString() = "$this = load $pointer"
}

class BinaryOp(name: String, type: BinaryOpType, left: Value, right: Value) :
        Instruction(name, type.returnType(left.type, right.type), 2) {
    var left by operand(0, left)
    var right by operand(0, right)

    override fun fullString() = "$name = $type $left, $right"
}