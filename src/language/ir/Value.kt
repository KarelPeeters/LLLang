package language.ir

abstract class Value(val type: Type) : Node() {
    val users = mutableSetOf<Node>()

    open val replaceAble = true

    /**
     * Replace all uses of this [Value] with [to]
     */
    open fun replaceWith(to: Value) {
        require(replaceAble) { "can't replace value $this of type ${this::class.java.name}" }

        for (user in this.users.toSet())
            user.replaceOperand(this, to)

        require(users.isEmpty()) { "value should have no users left after replacement" }
    }
}

class Constant private constructor(val value: Int, type: Type) : Value(type) {
    override val replaceAble = false
    override fun toString() = "$value $type"

    companion object {
        fun of(value: Int, type: Type) = Constant(value, type)
    }
}