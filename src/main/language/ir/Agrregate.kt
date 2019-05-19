package language.ir

abstract class AggregateType : Type() {
    abstract operator fun get(i: Int): Type
    abstract val size: Int
}

data class StructType(val name: String, val propertyTypes: List<Type>) : AggregateType() {
    override fun get(i: Int) = propertyTypes[i]
    override val size = propertyTypes.size

    override fun toString() = name
}

class StructValue(val stype: StructType, values: List<Value>) : Value(stype) {
    val values = operandList(values)

    override fun doVerify() {
        check(stype.propertyTypes.size == values.size) { "value sizes match" }
        for ((t, v) in stype.propertyTypes.zip(values))
            check(t == v.type) { "property types match" }
    }

    override fun str(env: NameEnv): String {
        val propStr = values.joinToString { it.str(env) }
        return "${stype.name}($propStr)"
    }
}

data class ArrayType(val inner: Type, override val size: Int) : AggregateType() {
    override fun get(i: Int) = inner
    override fun toString() = "[$inner, $size]"
}

class ArrayValue(val atype: ArrayType, values: List<Value>) : Value(atype) {
    val values = operandList(values)

    override fun doVerify() {
        check(atype.size == values.size)
        for (v in values)
            check(atype.inner == v.type) { "value types match" }
    }

    override fun str(env: NameEnv) =
            values.joinToString(prefix = "{", postfix = "}") { it.str(env) }
}