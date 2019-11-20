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

    protected inline fun <reified N : Node> operand(value: N? = null, type: Type? = null) =
            operand(value, N::class, type)

    protected fun <N : Node> operand(value: N?, cls: KClass<N>?, type: Type?): ReadWriteProperty<Node, N> = object : ReadWriteProperty<Node, N>, OperandHolder {
        @Suppress("ObjectPropertyName")
        var _value: N? = null

        init {
            if (value != null)
                setValue(value)
        }

        @Suppress("UNCHECKED_CAST")
        fun setValue(value: Node) {
            if (cls != null)
                check(cls.isInstance(value)) { "value $value is not an instance of $cls" }
            if (type != null)
                checkTypeEquals(value, type)
            this._value = value as N
        }

        fun getValue(): N = this._value ?: error("Attempt to get uninitialized operand")

        override fun getValue(thisRef: Node, property: KProperty<*>): N = getValue()
        override fun setValue(thisRef: Node, property: KProperty<*>, value: N): Unit = setValue(value)

        //the value is continuously typechecked, nothing to do here
        override fun typeCheck() {}

        override fun operands() = listOf(getValue())

        @Suppress("UNCHECKED_CAST")
        override fun replaceOperand(from: Node, to: Node) {
            if (getValue() == from)
                setValue(to)
        }
    }.also { holders += it }

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
            to as N
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
            to as V
            map.replaceAll { k, v ->
                if (k == from) error("Trying to replace key $k with $to")
                if (v == from) to else v
            }
        }
    }.also { holders += it }
}

private fun checkTypeEquals(value: Node, type: Type) = check(value.type == type) {
    "value $value has type ${value.type}, expected type $type"
}

private interface OperandHolder {
    fun typeCheck()
    fun operands(): Collection<Node>
    fun replaceOperand(from: Node, to: Node)
}
