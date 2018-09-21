package language.ir

sealed class Instruction(val name: String, type: Type) : Value(type) {
    override fun valueString() = "%$name $type"
}

class BinaryOp(name: String, val op: BinaryOpType, var left: Value, var right: Value) : Instruction(name, INT32) {
    override fun toString() = "${valueString()} = $op ${left.valueString()}, ${right.valueString()}"
}

class Alloc(name: String) : Instruction(name, PINT32) {
    override fun toString() = "${valueString()} = alloc"
}

class Store(var pointer: Value, var value: Value) : Instruction("", VOID) {
    override fun toString() = "store ${value.valueString()} -> ${pointer.valueString()}"
}

class Load(name: String, var pointer: Value) : Instruction(name, INT32) {
    override fun toString() = "${valueString()} = load ${pointer.valueString()}"
}