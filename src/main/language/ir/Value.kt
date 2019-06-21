package language.ir

abstract class Value(val type: Type) : Node() {
    val users = mutableSetOf<User>()

    protected open val replaceAble = true

    fun isUsed() = users.isNotEmpty()

    /**
     * Replace all uses of this [Value] with [to]
     */
    open fun replaceWith(to: Value) {
        require(replaceAble) { "can't replace value $this of type ${this::class.java.name}" }

        for (user in this.users.toSet())
            user.replaceOperand(this, to)

        check(users.isEmpty()) { "value should have no users left after replacement" }
    }

    abstract fun str(env: NameEnv): String
}

class Constant(type: Type, val value: Int) : Value(type) {
    init {
        require(type is IntegerType)
    }

    override val replaceAble = false
    override fun str(env: NameEnv) = "$value $type"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Constant

        return type == other.type && value == other.value
    }

    override fun hashCode(): Int {
        var result = value
        result = 31 * result + type.hashCode()
        return result
    }
}

class Undefined(type: Type) : Value(type) {
    override val replaceAble = false
    override fun str(env: NameEnv) = "undef $type"
}

object UnitValue : Value(UnitType) {
    override fun str(env: NameEnv) = "unit"
}