package language.ir

abstract class Value(val type: Type) : User() {
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

    fun str(env: NameEnv, withType: Boolean = true): String {
        val str = untypedStr(env)
        return if (withType) "$str $type" else str
    }

    abstract fun untypedStr(env: NameEnv): String
}

class Constant(type: Type, val value: Int) : Value(type) {
    init {
        require(type is IntegerType)
    }

    override val replaceAble = false

    override fun toString() = "$value $type"

    override fun untypedStr(env: NameEnv) = "$value"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Constant

        return type == other.type && value == other.value
    }

    override fun hashCode() = 31 * value + type.hashCode()
}

class UndefinedValue(type: Type) : Value(type) {
    override val replaceAble = false
    override fun untypedStr(env: NameEnv) = "undef"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as UndefinedValue

        return type == other.type
    }

    override fun hashCode() = type.hashCode()
}

object VoidValue : Value(VoidType) {
    override fun untypedStr(env: NameEnv) = "void"
}