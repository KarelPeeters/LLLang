package language.ir

abstract class Value(val type: Type) : Node() {
    val users = mutableSetOf<Node>()

    /**
     * Replace all uses of this [Value] with [to]
     */
    fun replaceWith(to: Value) {
        require(this !is Constant) { "can't replace constant value $this" }

        for (user in this.users.toSet())
            user.replaceOperand(this, to)

        require(users.isEmpty()) { "value should have no users left after replacement" }
    }
}

class Constant private constructor(val value: Int, type: Type) : Value(type) {
    override fun toString() = "$value $type"

    companion object {
        fun of(value: Int, type: Type) = Constant(value, type)
    }
}