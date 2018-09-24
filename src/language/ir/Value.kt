package language.ir

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

open class User(operandCount: Int) {
    private val _operands = arrayOfNulls<Value>(operandCount)

    protected fun operandCount() = _operands.size

    /**
     * Get the operator at [index]
     */
    protected operator fun get(index: Int) = _operands[index]!!

    /**
     * Set the operator at [index] to [value], updating the previous and new [Value.users].
     */
    protected operator fun set(index: Int, value: Value) {
        val prev = _operands[index]
        if (value == prev) return

        _operands[index] = value
        value.users += this

        if (prev != null && prev !in _operands) {
            prev.users -= this
        }
    }

    /**
     * Replace all operands of this [User] equal to [from] with [to], updating [Value.users]
     */
    fun replaceOperand(from: Value, to: Value) {
        for (i in 0 until operandCount())
            if (this[i] == from)
                this[i] = to
    }

    /**
     * Get a delegate that forwards calls to [get] and [set]
     * @param index Index in the `operands` array
     * @param value Initial value. If `null` the delegate behaves like `lateinit`
     */
    @Suppress("UNCHECKED_CAST")
    protected fun <T : Value> operand(index: Int, value: T? = null): ReadWriteProperty<User, T> {
        require(index in 0 until operandCount())

        val delegate = UserOperandDelegate.getInstance(index)
        if (value != null)
            this[index] = value
        return delegate as ReadWriteProperty<User, T>
    }

    private class UserOperandDelegate private constructor(val index: Int) : ReadWriteProperty<User, Value> {
        override fun getValue(thisRef: User, property: KProperty<*>): Value {
            return thisRef[index]
        }

        override fun setValue(thisRef: User, property: KProperty<*>, value: Value) {
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

abstract class Value(val type: Type, operandCount: Int) : User(operandCount) {
    val users = mutableSetOf<User>()

    /**
     * Replace all uses of this [Value] with [to]
     */
    fun replaceWith(to: Value) {
        for (user in this.users.toSet())
            user.replaceOperand(this, to)
    }
}

class Constant private constructor(val value: Int, type: Type) : Value(type, 0) {
    override fun toString() = "$value $type"

    companion object {
        fun of(value: Int, type: Type) = Constant(value, type)
    }
}
