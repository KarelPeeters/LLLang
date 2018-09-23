package language.ir

import language.ir.IntegerType.Companion.i32

sealed class Instruction(val name: String, type: Type, operandCount: Int) : Value(type, operandCount) {
    override fun toString() = "%$name $type"

    abstract fun fullString(): String
}

class Alloc(name: String) : Instruction(name, i32, 0) {
    override fun fullString() = "$this = alloc"
}

class BinaryOp(name: String, val op: BinaryOpType, left: Value, right: Value) : Instruction(name, i32, 2) {
    var left by operand(0, left)
    var right by operand(0, right)

    override fun fullString() = "$this = $op $left, $right"
}

class Store(pointer: Value, value: Value) : Instruction("", VoidType, 2) {
    var pointer by operand(0, pointer)
    var value by operand(1, value)

    override fun fullString() = "store $value -> $pointer"
}

class Load(name: String, pointer: Value) : Instruction(name, i32, 1) {
    var pointer by operand(0, pointer)

    override fun fullString() = "$this = load $pointer"
}