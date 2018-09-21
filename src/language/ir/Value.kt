package language.ir

interface Type

object VOID : Type {
    override fun toString() = "void"
}
object INT32 : Type {
    override fun toString() = "i32"
}
object PINT32 : Type {
    override fun toString() = "i32*"
}

abstract class Value(val type: Type) {
    abstract fun valueString(): String
}

class Constant private constructor(val value: Int) : Value(INT32) {
    override fun valueString() = value.toString()

    companion object {
        val ZERO = Constant(0)
        val ONE = Constant(1)

        fun of(value: Int) = when (value) {
            0 -> ZERO
            1 -> ONE
            else -> Constant(value)
        }
    }
}
