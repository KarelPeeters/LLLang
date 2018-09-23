package language.ir

sealed class Instruction(val name: String, type: Type) : Value(type) {
    override fun valueString() = "%$name $type"
}

class BinaryOp(name: String, val op: BinaryOpType, var left: Value, var right: Value) : Instruction(name, INT32) {
    override fun toString() = "${valueString()} = $op ${left.valueString()}, ${right.valueString()}"
}

class Store(var pointer: Alloc, var value: Value) : Instruction("", VOID) {
    override fun toString() = "store ${value.valueString()} -> ${pointer.valueString()}"
}

class Load(name: String, var pointer: Alloc) : Instruction(name, INT32) {
    override fun toString() = "${valueString()} = load ${pointer.valueString()}"
}