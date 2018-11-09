package language.ir

abstract class Value(val type: Type) : Node() {
    val users = mutableSetOf<Node>()

    protected open val replaceAble = true

    /**
     * Replace all uses of this [Value] with [to]
     */
    open fun replaceWith(to: Value) {
        require(replaceAble) { "can't replace value $this of type ${this::class.java.name}" }

        for (user in this.users.toSet())
            user.replaceOperand(this, to)

        require(users.isEmpty()) { "value should have no users left after replacement" }
    }

    open fun str(env: NameEnv) = toString()
}

class Constant constructor(type: Type, val value: Int) : Value(type) {
    override val replaceAble = false
    override fun str(env: NameEnv) = "$value $type"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Constant
        if (value != other.value) return false
        if (replaceAble != other.replaceAble) return false

        return true
    }

    override fun hashCode(): Int {
        var result = value
        result = 31 * result + replaceAble.hashCode()
        return result
    }
}