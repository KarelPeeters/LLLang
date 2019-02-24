package     language.ir

import language.util.Bag
import language.util.toBag
import javax.naming.OperationNotSupportedException
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

@Suppress("UNCHECKED_CAST")
abstract class Node {
    var deleted = false
        private set
    private val holders = mutableListOf<OperandHolder>()
    private val useCounts = Bag<Value>()

    protected fun <T : Value> operand(value: T? = null): ReadWriteProperty<Node, T> =
            OperandBox(value).also { holders += it }

    protected fun <T : Value> operandList(values: List<T>? = null): OperandList<T> =
            OperandList(values).also { holders += it }

    protected fun <K : Value, V : Value> operandMap(values: Map<K, V>? = null): OperandMap<K, V> =
            OperandMap(values).also { holders += it }

    private fun changeUseCount(value: Value, delta: Int) {
        val prev = useCounts.get(value)
        val count = useCounts.add(value, delta)

        if (count == 0) {
            check(prev != 0)
            value.users -= this
        } else if (prev == 0) {
            check(count != 0)
            value.users += this
        }
    }

    /** Shallow delete this Node: _only_ remove this Node as user of its operand */
    fun shallowDelete() {
        for (operand in operands)
            check(operand.users.remove(this))

        for (holder in holders)
            holder.clearOperands()
        holders.clear()

        this.deleted = true
    }

    fun replaceOperand(from: Value, to: Value): Boolean =
            holders.any { it.replaceOperand(from, to) }

    val operands get() = useCounts.keys

    /** Check this and operand deletion, then call [doVerify] */
    open fun verify() {
        check(!this.deleted) { "$this can't be deleted" }
        val counts = holders.asSequence().flatMap { it.operands() }.groupingBy { it }.eachCount().toBag()
        check(counts == useCounts) { "counts must be correct" }

        for (operand in operands) {
            check(!operand.deleted) { "operands can't be deleted" }
            check(this in operand.users) { "operand $operand must know it's used by $this" }
        }

        doVerify()
    }

    protected abstract fun doVerify()

    private interface OperandHolder {
        fun operands(): Sequence<Value>

        fun replaceOperand(from: Value, to: Value): Boolean

        fun clearOperands()
    }

    inner class OperandBox<T : Value> : OperandHolder, ReadWriteProperty<Node, T> {
        var value: T? = null

        constructor(value: T?) {
            this.value = value
            if (value != null)
                changeUseCount(value, 1)
        }

        override fun operands() = sequenceOf(value!!)

        override fun replaceOperand(from: Value, to: Value): Boolean {
            if (value == from) {
                value = to as T
                changeUseCount(from, -1)
                changeUseCount(to, 1)
                return true
            }
            return false
        }

        override fun clearOperands() {
            value = null
        }

        override fun getValue(thisRef: Node, property: KProperty<*>): T = value!!

        override fun setValue(thisRef: Node, property: KProperty<*>, value: T) {
            this.value?.let { prev -> changeUseCount(prev, -1) }
            changeUseCount(value, 1)
            this.value = value
        }

        override fun toString() = "[value]"
    }

    inner class OperandList<T : Value> : OperandHolder, AbstractMutableList<T> {
        private val list = mutableListOf<Value>()

        constructor(list: List<T>?) : super() {
            if (list != null) {
                this.list.addAll(list)
                for (element in list)
                    changeUseCount(element, 1)
            }
        }

        override fun operands() = list.asSequence()

        override fun replaceOperand(from: Value, to: Value): Boolean {
            var count = 0
            for (i in indices) {
                if (list[i] == from) {
                    count++
                    list[i] = to as T
                }
            }
            if (count != 0) {
                changeUseCount(from, -count)
                changeUseCount(to, count)
                return true
            }
            return false
        }

        override fun clearOperands() {
            list.clear()
        }

        //list implementation
        override val size get() = list.size

        override fun add(index: Int, element: T) {
            list.add(index, element)
            changeUseCount(element, 1)
        }

        override fun get(index: Int): T {
            return list[index] as T
        }

        override fun set(index: Int, element: T): T {
            val prev = list.set(index, element)
            changeUseCount(prev, -1)
            changeUseCount(element, 1)
            return prev as T
        }

        override fun removeAt(index: Int): T {
            val prev = list.removeAt(index)
            changeUseCount(prev, -1)
            return prev as T
        }

        override fun toString() = list.toString()
    }

    inner class OperandMap<K : Value, V : Value> : OperandHolder, MutableMap<K, V> {
        val map = mutableMapOf<K, V>()

        constructor(map: Map<K, V>?) {
            if (map != null) {
                this.map.putAll(map)
                for ((k, v) in map) {
                    changeUseCount(k, 1)
                    changeUseCount(v, 1)
                }
            }
        }

        override fun operands() = map.keys.asSequence() + map.values.asSequence()

        override fun replaceOperand(from: Value, to: Value): Boolean {
            var fromDelta = 0
            var toDelta = 0

            for (entry in map) {
                if (entry.value == from) {
                    entry.setValue(to as V)
                    fromDelta++
                    toDelta++
                }
            }

            val fromValue = map[from]
            val toValue = map[to]

            if (fromValue != null) {
                if (toValue != null) {
                    //duplicate key, check that values are the same
                    require(fromValue == toValue) { "key collision $from ($fromValue) to $to ($toValue)" }
                } else {
                    map[to as K] = fromValue
                    toDelta++
                }
                map.remove(from)
                fromDelta++
            }

            changeUseCount(from, -fromDelta)
            changeUseCount(to, toDelta)

            return fromDelta != 0
        }

        override fun clearOperands() {
            map.clear()
        }

        //map implementation
        override val size: Int get() = map.size

        override fun containsKey(key: K): Boolean = map.containsKey(key)
        override fun containsValue(value: V): Boolean = map.containsValue(value)
        override fun isEmpty(): Boolean = map.isEmpty()

        override fun clear() {
            for ((k, v) in map) {
                changeUseCount(k, -1)
                changeUseCount(v, -1)
            }
            map.clear()
        }

        override fun get(key: K): V? = map[key]

        override fun put(key: K, value: V): V? {
            val prev = map.put(key, value)
            if (prev != null) {
                changeUseCount(prev, -1)
            } else {
                changeUseCount(key, 1)
            }
            changeUseCount(value, 1)
            return prev
        }

        override fun putAll(from: Map<out K, V>) {
            for ((k, v) in from)
                put(k, v)
        }

        override fun remove(key: K): V? {
            val prev = map.remove(key)
            if (prev != null)
                changeUseCount(prev, -1)
            changeUseCount(key, -1)
            return prev
        }

        override fun toString() = map.toString()

        override val entries: MutableSet<MutableMap.MutableEntry<K, V>> = Entries()
        override val keys: MutableSet<K> = Keys()
        override val values: MutableCollection<V> = Values()

        inner class Entries : MutableSet<MutableMap.MutableEntry<K, V>>, AbstractMutableSet<MutableMap.MutableEntry<K, V>>() {
            override val size: Int get() = map.size

            override fun add(element: MutableMap.MutableEntry<K, V>) = throw OperationNotSupportedException()

            override fun iterator() = object : MutableIterator<MutableMap.MutableEntry<K, V>> {
                val iter = map.entries.iterator()
                lateinit var prev: MutableMap.MutableEntry<K, V>

                override fun hasNext(): Boolean = iter.hasNext()

                override fun next(): MutableMap.MutableEntry<K, V> = iter.next().also { prev = it }

                override fun remove() {
                    iter.remove()
                    changeUseCount(prev.key, -1)
                    changeUseCount(prev.value, -1)
                }
            }
        }

        inner class Keys : MutableSet<K>, AbstractMutableSet<K>() {
            override val size: Int get() = map.size

            override fun add(element: K): Boolean = throw UnsupportedOperationException()

            override fun iterator() = object : MutableIterator<K> {
                val iter = map.entries.iterator()
                lateinit var prev: Map.Entry<K, V>

                override fun hasNext(): Boolean = iter.hasNext()

                override fun next(): K {
                    prev = iter.next()
                    return prev.key
                }

                override fun remove() {
                    iter.remove()
                    changeUseCount(prev.key, -1)
                    changeUseCount(prev.value, -1)
                }
            }

            override fun contains(element: K): Boolean = map.containsKey(element)
        }

        inner class Values : MutableCollection<V>, AbstractMutableCollection<V>() {
            override val size: Int get() = map.size

            override fun iterator() = object : MutableIterator<V> {
                val iter = map.entries.iterator()
                lateinit var prev: Map.Entry<K, V>

                override fun hasNext(): Boolean = iter.hasNext()

                override fun next(): V {
                    prev = iter.next()
                    return prev.value
                }

                override fun remove() {
                    iter.remove()
                    changeUseCount(prev.key, -1)
                    changeUseCount(prev.value, -1)
                }
            }

            override fun add(element: V): Boolean = throw OperationNotSupportedException()

            override fun contains(element: V): Boolean = map.containsValue(element)
        }
    }
}