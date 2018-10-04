package language.ir

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

abstract class Node {
    private var nextOperandIndex = 0
    private val operands = mutableListOf<Value?>()

    fun delete() {
        for (operand in operands)
            operand?.users?.remove(this)
        operands.clear()
    }

    fun replaceOperand(from: Value, to: Value) {
        operands.replaceAll { if (it === from) to else it }
        from.users -= this
        to.users += this
    }

    private operator fun get(index: Int): Value = operands[index]!!

    private operator fun set(index: Int, value: Value) {
        val prev = operands[index]
        if (value === prev) return

        operands[index] = value
        value.users += this

        if (prev != null && prev !in operands)
            prev.users -= this
    }

    @Suppress("UNCHECKED_CAST")
    protected inline fun <reified T : Value> operand(value: T? = null) = operand(value) as ReadWriteProperty<Node, T>

    @JvmName("_operand")
    protected fun operand(value: Value? = null): ReadWriteProperty<Node, Value> {
        val index = nextOperandIndex++

        val delegate = UserOperandDelegate.getInstance(index)
        this.operands.add(value)
        return delegate
    }

    private class UserOperandDelegate private constructor(val index: Int) : ReadWriteProperty<Node, Value> {
        override fun getValue(thisRef: Node, property: KProperty<*>): Value {
            return thisRef[index]
        }

        override fun setValue(thisRef: Node, property: KProperty<*>, value: Value) {
            thisRef[index] = value
        }

        companion object {
            private val instances = mutableListOf<UserOperandDelegate>()

            fun getInstance(index: Int): UserOperandDelegate {
                for (i in instances.size..index)
                    instances.add(UserOperandDelegate(index))
                return instances[index]
            }
        }
    }
}