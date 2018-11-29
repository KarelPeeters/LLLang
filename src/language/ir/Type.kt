package language.ir

abstract class Type

class IntegerType private constructor(val width: Int) : Type() {
    override fun toString() = "i$width"

    companion object {
        private val map = mutableMapOf<Int, IntegerType>()
        fun width(bits: Int) = map.getOrPut(bits) { IntegerType(bits) }

        val bool = width(1)
        val i32 = width(32)
    }
}

class PointerType private constructor(val inner: Type) : Type() {
    override fun toString() = "$inner*"

    companion object {
        private val map = mutableMapOf<Type, PointerType>()
        fun pointTo(type: Type) = map.getOrPut(type) { PointerType(type) }
    }
}

val Type.pointer get() = PointerType.pointTo(this)
val Type.unpoint get() = (this as? PointerType)?.inner

object UnitType : Type() {
    override fun toString() = "Unit"
}