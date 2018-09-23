package language.ir

sealed class Terminator(operandCount: Int): Value(VoidType, operandCount)

class Branch(value: Value, ifTrue: BasicBlock, ifFalse: BasicBlock) : Terminator(3) {
    var value by operand(0, value)
    var ifTrue by operand(1, ifTrue)
    var ifFalse by operand(2, ifFalse)

    override fun toString() = "branch $value T $ifTrue F $ifFalse"
}

class Jump(target: BasicBlock) : Terminator(1) {
    var target by operand(0, target)

    override fun toString() = "jump $target"
}

object Exit : Terminator(0) {
    override fun toString() = "exit"
}