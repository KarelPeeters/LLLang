package language.ir

import language.util.Bag
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * A [User] is something that uses values. This interface and the associated [UserHandler] provide property delegates
 * that automaticcaly keep track of used values and update [Value.users] accrodingly.
 */
interface User {
    /** Stores the usage state for this User. This property is not intended to be used outside implmenting class.
     *  Classes that implement [User] should override and use [handler] as follows:
     *
     * ````
     * override val handler = UserHandler(this)
     * val myOperand by handler.operand<Value>()
     * ````
     */
    val handler: UserHandler

    /** The set of used values */
    val operands: Set<Value> get() = handler.operands

    /** Replace a value with another in this user */
    fun replaceOperand(from: Value, to: Value) = handler.replaceOperand(from, to)

    /** Drop usages of all operands, this invalides the delegates:
     * any further access to the delegates will throw an exception*/
    fun dropOperands() = handler.dropOperands()

    fun <T : Value> operand(value: T? = null) = handler.operand(value)
    fun <T : Value> operandList(values: List<T>? = null) = handler.operandList(values)
    fun <K : Value, V : Value> operandMap(values: Map<K, V>? = null) = handler.operandMap(values)
}

@Suppress("unchecked_cast")
class UserHandler(val user: User) {
    private var dropped = false
    private val holders = mutableListOf<OperandHolder>()
    private val useCounts = Bag<Value>()

    val operands: Set<Value> get() = useCounts.keys

    fun replaceOperand(from: Value, to: Value) {
        if (from in useCounts) {
            for (holder in holders)
                holder.replaceOperand(from, to)
            useCounts.add(to, useCounts.remove(from))
        }
    }

    fun dropOperands() {
        check(!dropped)
        this.dropped = true

        for (holder in holders)
            holder.clearOperands()
        for (operand in operands)
            check(operand.users.remove(user))
        useCounts.clear()
    }

    private fun changeUseCount(value: Value, delta: Int) {
        if (delta == 0) return

        val prev = useCounts.get(value)
        val count = useCounts.add(value, delta)

        check(count >= 0) { "count can't be negative" }

        if (count == 0) {
            check(prev != 0)
            value.users -= user
        } else if (prev == 0) {
            check(count != 0)
            value.users += user
        }
    }

    fun <T : Value> operand(value: T?): ReadWriteProperty<User, T> =
            OperandBox(value).also { holders += it }

    fun <T : Value> operandList(values: List<T>?): ReadOnlyProperty<User, MutableList<T>> =
            BasicDelegate(OperandList(values).also { holders += it })

    fun <K : Value, V : Value> operandMap(values: Map<K, V>?): ReadOnlyProperty<User, MutableMap<K, V>> =
            BasicDelegate(OperandMap(values).also { holders += it })

    private inner class OperandBox<T : Value> : OperandHolder, ReadWriteProperty<User, T> {
        var value: T? = null

        constructor(value: T?) {
            this.value = value
            if (value != null)
                changeUseCount(value, 1)
        }

        override fun operands() = sequenceOf(value!!)

        override fun replaceOperand(from: Value, to: Value) {
            if (value == from) {
                value = to as T
                changeUseCount(from, -1)
                changeUseCount(to, 1)
            }
        }

        override fun clearOperands() {
            value = null
        }

        override fun getValue(thisRef: User, property: KProperty<*>): T = value!!

        override fun setValue(thisRef: User, property: KProperty<*>, value: T) {
            this.value?.let { prev -> changeUseCount(prev, -1) }
            changeUseCount(value, 1)
            this.value = value
        }

        override fun toString() = "[value]"
    }

    private inner class OperandList<T : Value> : OperandHolder, AbstractMutableList<T> {
        private val list = mutableListOf<Value>()

        constructor(list: List<T>?) : super() {
            if (list != null) {
                this.list.addAll(list)
                for (element in list)
                    changeUseCount(element, 1)
            }
        }

        override fun operands() = list.asSequence()

        override fun replaceOperand(from: Value, to: Value) {
            val iter = list.listIterator()

            var count = 0
            for (value in iter) {
                if (value == from) {
                    iter.set(to)
                    count++
                }
            }

            changeUseCount(from, -count)
            changeUseCount(to, count)
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

    private inner class OperandMap<K : Value, V : Value> : OperandHolder, MutableMap<K, V> {
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

        override fun replaceOperand(from: Value, to: Value) {
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

            override fun add(element: MutableMap.MutableEntry<K, V>) = throw UnsupportedOperationException()

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

            override fun add(element: V): Boolean = throw UnsupportedOperationException()

            override fun contains(element: V): Boolean = map.containsValue(element)
        }
    }
}

private class BasicDelegate<out T>(val value: T) : ReadOnlyProperty<Any, T> {
    override fun getValue(thisRef: Any, property: KProperty<*>) = value
}

private interface OperandHolder {
    fun operands(): Sequence<Value>

    /** doesn't report changes back */
    fun replaceOperand(from: Value, to: Value)

    /** doesn't report changes back */
    fun clearOperands()
}