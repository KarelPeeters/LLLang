package language.ir

sealed class Type {
    abstract override fun toString(): String
}

data class PointerType constructor(val inner: Type) : Type() {
    override fun toString() = "*$inner"
}

val Type.pointer: PointerType get() = PointerType(this)
val Type.unpoint: Type? get() = (this as? PointerType)?.inner

data class FunctionType(val parameters: List<Type>, val returns: List<Type>) : Type() {
    override fun toString() = "($parameters) -> ($returns)"
}

data class TupleType(val types: List<Type>) : Type() {
    constructor(vararg types: Type) : this(types.asList())

    override fun toString() = types.joinToString(prefix = "(", postfix = ")") { it.toString() }

    operator fun get(index: Int) = types[index]
}

data class IntegerType(val width: Int) : Type() {
    init {
        check(width > 0) { "width must be positve, got $width" }
    }

    companion object {
        val bool = IntegerType(1)
        val i32 = IntegerType(32)
    }

    override fun toString() = "i$width"
}

object VoidType : Type() {
    override fun toString() = "Void"
}

object RegionType : Type() {
    override fun toString() = "Region"
}

object MemType : Type() {
    override fun toString() = "Mem"
}