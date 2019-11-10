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

    fun operands(): Collection<Node> = holders.flatMap { it.operands() }

    fun replaceOperand(from: Node, to: Node) {
        for (holder in holders)
            holder.replaceOperand(from, to)
    }

    protected inline fun <reified N : Node> operand(value: N? = null, type: Type? = null) =
            operand(value, N::class, type)

    protected fun <N : Node> operand(value: N?, cls: KClass<N>?, type: Type?): ReadWriteProperty<Node, N> = object : ReadWriteProperty<Node, N>, OperandHolder {
        @Suppress("ObjectPropertyName")
        var _value: N? = null

        @Suppress("UNCHECKED_CAST")
        fun setValue(value: Node) {
            if (cls != null)
                check(cls.isInstance(value)) { "value $value is not an instance of $cls" }
            if (type != null)
                check(value.type == type) { "value $value has type ${value.type}, expected type $type" }
            this._value = value as N
        }

        fun getValue(): N = this._value ?: error("Attempt to get uninitialized operand")

        override fun getValue(thisRef: Node, property: KProperty<*>): N = getValue()
        override fun setValue(thisRef: Node, property: KProperty<*>, value: N): Unit = setValue(value)

        override fun operands() = listOf(getValue())

        @Suppress("UNCHECKED_CAST")
        override fun replaceOperand(from: Node, to: Node) {
            if (getValue() == from)
                setValue(to)
        }
    }.also {
        if (value != null)
            it.setValue(value)
        holders += it
    }

    protected fun <N : Node> operandList(values: List<N>? = null): ReadOnlyProperty<Node, MutableList<N>> = object : ReadOnlyProperty<Node, MutableList<N>>, OperandHolder {
        val list = values.orEmpty().toMutableList()

        override fun getValue(thisRef: Node, property: KProperty<*>) = list

        override fun operands() = list

        @Suppress("UNCHECKED_CAST")
        override fun replaceOperand(from: Node, to: Node) {
            to as N
            list.replaceAll { if (it == from) to else it }
        }
    }.also { holders += it }

    protected fun <K, V : Node> operandValueMap(values: Map<K, V>? = null): ReadOnlyProperty<Node, MutableMap<K, V>> = object : ReadOnlyProperty<Node, MutableMap<K, V>>, OperandHolder {
        val map = values.orEmpty().toMutableMap()

        override fun getValue(thisRef: Node, property: KProperty<*>) = map

        override fun operands() = map.values

        @Suppress("UNCHECKED_CAST")
        override fun replaceOperand(from: Node, to: Node) {
            to as V
            map.replaceAll { k, v ->
                if (k == from) error("Trying to replace key $k with $to")
                if (v == from) to else v
            }
        }
    }.also { holders += it }
}

private interface OperandHolder {
    fun operands(): Collection<Node>
    fun replaceOperand(from: Node, to: Node)
}
