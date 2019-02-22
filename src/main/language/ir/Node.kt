package language.ir

import language.util.replace
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

abstract class Node {
    private var operandList: OperandList? = null

    open val operands: List<Value>
        get() {
            operandList?.operands?.let { list ->
                check(list.all { it != null })
                @Suppress("UNCHECKED_CAST")
                return list as List<Value>
            }

            return emptyList()
        }

    /**
     * Shallow delete this Node: _only_ remove this Node as user of its operands
     */
    open fun shallowDelete() {
        operandList?.apply { delete() }
    }

    open fun replaceOperand(from: Value, to: Value) {
        operandList?.apply { replaceOperand(from, to) }
    }

    abstract fun verify()

    @Suppress("UNCHECKED_CAST")
    protected inline fun <reified T : Value> operand(value: T? = null) = operand(value) as ReadWriteProperty<Node, T>

    @JvmName("_operand")
    protected fun operand(value: Value? = null): ReadWriteProperty<Node, Value> {
        val list = operandList ?: OperandList()
        operandList = list

        val index = list.addOperand(value)
        return UserOperandDelegate.getInstance(index)
    }

    private inner class OperandList {
        var nextOperandIndex = 0
        val operands = mutableListOf<Value?>()

        fun delete() {
            for (operand in operands)
                operand?.users?.remove(this@Node)
            operands.clear()
        }

        fun replaceOperand(from: Value, to: Value) {
            if (operands.replace(from, to)) {
                from.users -= this@Node
                to.users += this@Node
            }
        }

        fun getOperand(index: Int): Value = operands[index]!!

        fun setOperand(index: Int, value: Value) {
            val prev = operands[index]
            if (value == prev) return

            operands[index] = value
            value.users += this@Node

            if (prev != null && prev !in operands)
                prev.users -= this@Node
        }

        fun addOperand(value: Value?): Int {
            val index = nextOperandIndex++
            operands.add(value)
            value?.users?.add(this@Node)
            return index
        }
    }

    private class UserOperandDelegate private constructor(val index: Int) : ReadWriteProperty<Node, Value> {
        override fun getValue(thisRef: Node, property: KProperty<*>): Value {
            return thisRef.operandList!!.getOperand(index)
        }

        override fun setValue(thisRef: Node, property: KProperty<*>, value: Value) {
            thisRef.operandList!!.setOperand(index, value)
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

