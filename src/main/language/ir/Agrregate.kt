package language.ir

sealed class AggregateType : Type() {
    abstract val innerTypes: List<Type>
    abstract val size: Int
}

data class StructType(val name: String, val properties: List<Type>) : AggregateType() {
    override val size get() = properties.size

    override val innerTypes get() = properties
    override fun toString() = "%$name"

    fun fullString() = properties.joinToString(prefix = "{", postfix = "}") { it.toString() }
}

data class ArrayType(val inner: Type, override val size: Int) : AggregateType() {
    override val innerTypes = List(size) { inner }

    override fun toString() = "[$inner, $size]"
}