package language.ir

import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

abstract class NodeImpl {
    init {
        check(this is Node)
    }

    private val holders = mutableListOf<OperandHolder>()

    fun typeCheck() {
        for (holder in holders)
            holder.typeCheck()
    }

    fun operands(): Collection<Node> = holders.flatMap { it.operands() }

    fun replaceOperand(from: Node, to: Node) {
        for (holder in holders)
            holder.replaceOperand(from, to)
    }

    protected inline fun <reified N : Node> operand(value: N? = null, type: Type? = null): ReadWriteProperty<Node, N> =
            requiredOperand(value, N::class, type)

    protected inline fun <reified N : Node> optionalOperand(value: N? = null, type: Type? = null): ReadWriteProperty<Node, N?> =
            optionalOperand(value, N::class, type)

    protected fun <N : Node> requiredOperand(value: N?, cls: KClass<N>?, type: Type?): ReadWriteProperty<Node, N> = object : ReadWriteProperty<Node, N>, OperandHolder {
        var curr: N? = null

        fun setValue(value: Node) {
            curr = checkValue(value, cls, type)
        }

        fun getValue(): N = curr ?: error("Attempt to get uninitialized operand")

        override fun getValue(thisRef: Node, property: KProperty<*>): N = getValue()

        override fun setValue(thisRef: Node, property: KProperty<*>, value: N) = setValue(value)

        override fun typeCheck() {
            //only check that value has been set
            getValue()
        }

        override fun operands(): Collection<Node> = listOf(getValue())

        override fun replaceOperand(from: Node, to: Node) {
            if (getValue() == from)
                setValue(to)
        }
    }.also {
        if (value != null)
            it.setValue(value)
        holders += it
    }

    protected fun <N : Node> optionalOperand(value: N?, cls: KClass<N>, type: Type?): ReadWriteProperty<Node, N?> = object : ReadWriteProperty<Node, N?>, OperandHolder {
        var curr: N? = null

        fun setValue(value: N?) {
            if (value != null)
                checkValue(value, cls, type)
            curr = value
        }

        override fun getValue(thisRef: Node, property: KProperty<*>): N? = curr

        override fun setValue(thisRef: Node, property: KProperty<*>, value: N?) = setValue(value)

        override fun typeCheck() {}

        override fun operands(): Collection<Node> = curr?.let(::listOf) ?: emptyList()

        @Suppress("UNCHECKED_CAST")
        override fun replaceOperand(from: Node, to: Node) {
            if (curr == from)
                setValue(to as N)
        }
    }.also {
        if (value != null)
            it.setValue(value)
        holders += it
    }

    protected fun <N : Node> operandList(): ReadOnlyProperty<Node, MutableList<N>> =
            operandList(values = null, types = null, type = null)

    protected fun <N : Node> operandList(values: List<N>? = null, types: List<Type>): ReadOnlyProperty<Node, MutableList<N>> =
            operandList(values = values, types = types, type = null)

    protected fun <N : Node> operandList(values: List<N>? = null, type: Type): ReadOnlyProperty<Node, MutableList<N>> =
            operandList(values = values, types = null, type = type)

    private fun <N : Node> operandList(values: List<N>?, types: List<Type>?, type: Type?): ReadOnlyProperty<Node, MutableList<N>> = object : ReadOnlyProperty<Node, MutableList<N>>, OperandHolder {
        val list = values.orEmpty().toMutableList()

        override fun getValue(thisRef: Node, property: KProperty<*>) = list

        override fun typeCheck() {
            if (types != null) {
                val actual = list.map { it.type }
                check(actual == types) {
                    "values $list have types $actual, expected types $types"
                }
            }
            if (type != null) {
                for (value in list)
                    checkTypeEquals(value, type)
            }
        }

        override fun operands() = list

        @Suppress("UNCHECKED_CAST")
        override fun replaceOperand(from: Node, to: Node) {
            val to = checkValue<N>(to, null, type)
            list.replaceAll { if (it == from) to else it }
        }
    }.also { holders += it }

    protected fun <K, V : Node> operandValueMap(values: Map<K, V>? = null, type: Type? = null): ReadOnlyProperty<Node, MutableMap<K, V>> = object : ReadOnlyProperty<Node, MutableMap<K, V>>, OperandHolder {
        val map = values.orEmpty().toMutableMap()

        override fun getValue(thisRef: Node, property: KProperty<*>) = map

        override fun typeCheck() {
            if (type != null) {
                for (value in map.values)
                    checkTypeEquals(value, type)
            }
        }

        override fun operands() = map.values

        @Suppress("UNCHECKED_CAST")
        override fun replaceOperand(from: Node, to: Node) {
            val to = checkValue<V>(to, null, type)
            map.replaceAll { k, v ->
                if (k == from) error("Trying to replace key $k with $to")
                if (v == from) to else v
            }
        }
    }.also { holders += it }
}

@Suppress("UNCHECKED_CAST")
private fun <T : Node> checkValue(value: Node, cls: KClass<T>?, type: Type?): T {
    if (cls != null)
        check(cls.isInstance(value)) { "value $value is not an instance of $cls" }
    if (type != null)
        checkTypeEquals(value, type)
    return value as T
}

private fun checkTypeEquals(value: Node, type: Type) = check(value.type == type) {
    "value $value has type ${value.type}, expected type $type"
}

private interface OperandHolder {
    fun typeCheck()
    fun operands(): Collection<Node>
    fun replaceOperand(from: Node, to: Node)
}
