package language.ir

import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
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

    protected fun <N : Node> operand(value: N? = null): ReadWriteProperty<Node, N> = object : ReadWriteProperty<Node, N>, OperandHolder {
        lateinit var value: N

        init {
            value?.let { this.value = it }
        }

        override fun getValue(thisRef: Node, property: KProperty<*>): N = this.value

        override fun setValue(thisRef: Node, property: KProperty<*>, value: N) {
            this.value = value
        }

        override fun operands() = listOf(this.value)

        @Suppress("UNCHECKED_CAST")
        override fun replaceOperand(from: Node, to: Node) {
            to as N
            if (this.value == from)
                this.value = to
        }
    }.also { holders += it }

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
