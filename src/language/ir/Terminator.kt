package language.ir

sealed class Terminator : Value(VoidType)

class Branch(value: Value, ifTrue: BasicBlock, ifFalse: BasicBlock) : Terminator() {
    var value by operand(value)
    var ifTrue by operand<BasicBlock>(ifTrue)
    var ifFalse by operand<BasicBlock>(ifFalse)

    override fun toString() = "branch $value T $ifTrue F $ifFalse"
}

class Jump(target: BasicBlock) : Terminator() {
    var target by operand(target)

    override fun toString() = "jump $target"
}

object Exit : Terminator() {
    override fun toString() = "exit"
}