package language.ir

data class StructType(val name: String, val properties: List<Type>) : Type() {
    override fun toString() = name
}

class StructValue(val stype: StructType, val values: List<Value>) : Value(stype) {
    override fun doVerify() {
        check(stype.properties.size == values.size) { "value sizes match" }
        for ((t, v) in stype.properties.zip(values))
            check(t == v.type) { "property types match" }
    }

    override fun str(env: NameEnv): String {
        val propStr = values.joinToString { it.str(env) }
        return "${stype.name}($propStr)"
    }
}