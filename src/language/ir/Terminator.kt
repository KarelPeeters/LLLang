package language.ir

sealed class Terminator : Node()

class Branch(value: Value, var ifTrue: BasicBlock, var ifFalse: BasicBlock) : Terminator() {
    var value by operand(value)

    override fun toString() = "branch $value T $ifTrue F $ifFalse"
}

class Jump(var target: BasicBlock) : Terminator() {
    override fun toString() = "jump $target"
}

object Exit : Terminator() {
    override fun toString() = "exit"
}