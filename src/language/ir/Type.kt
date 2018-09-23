package language.ir

interface Type
class IntegerType private constructor(val width: Int): Type {
    override fun toString() = "i$width"

    companion object {
        private val map = mutableMapOf<Int, IntegerType>()
        fun width(bits: Int) = map.getOrPut(bits) { IntegerType(bits) }

        val bool = width(1)
        val i32 = width(32)
    }
}

class PointerType private constructor(val inner: Type): Type {
    override fun toString() = "$inner*"
}

object VoidType : Type {
    override fun toString() = "void"
}