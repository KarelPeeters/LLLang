package language.ir

sealed class Terminator : Node() {
    abstract fun fullStr(env: NameEnv): String
}

class Branch(value: Value, ifTrue: BasicBlock, ifFalse: BasicBlock) : Terminator() {
    var ifTrue by operand<BasicBlock>(ifTrue)
    var ifFalse by operand<BasicBlock>(ifFalse)
    var value by operand(value)

    override fun fullStr(env: NameEnv) = "branch ${value.str(env)} T ${ifTrue.str(env)} F ${ifFalse.str(env)}"
}

class Jump(target: BasicBlock) : Terminator() {
    var target by operand<BasicBlock>(target)

    override fun fullStr(env: NameEnv) = "jump ${target.str(env)}"
}

object Exit : Terminator() {
    override fun fullStr(env: NameEnv) = "exit"
}