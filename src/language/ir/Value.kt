package language.ir

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

open class User(operandCount: Int) {
    private val operands = arrayOfNulls<Value>(operandCount)

    fun replaceValue(from: Value, to: Value) {
        for (i in operands.indices)
            if (operands[i] == from)
                operands[i] = to
    }

    /**
     * Get a delegate that writes trough to the internal `operands` array and updates `Value.users` accordingly.
     * @param index Index in the `operands` array
     * @param value Initial value. If `null` the delegate behaves like `lateinit`
     */
    @Suppress("UNCHECKED_CAST")
    protected fun <T : Value> operand(index: Int, value: T? = null): ReadWriteProperty<User, T> {
        require(index in operands.indices)

        val delegate = UserOperandDelegate.getInstance(index)
        if (value != null)
            delegate.setValue(this, User::operands, value) //pass in a random property, UserOperandDelegate does't care
        return delegate as ReadWriteProperty<User, T>
    }

    private class UserOperandDelegate private constructor(val index: Int) : ReadWriteProperty<User, Value> {
        override fun getValue(thisRef: User, property: KProperty<*>): Value {
            return thisRef.operands[index]!!
        }

        override fun setValue(thisRef: User, property: KProperty<*>, value: Value) {
            val prev = thisRef.operands[index]
            if (value == prev) return

            thisRef.operands[index] = value
            value.users += thisRef

            if (prev != null && prev !in thisRef.operands) {
                prev.users -= thisRef
            }
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
}

class Constant private constructor(val value: Int) : Value(IntegerType.i32, 0) {
    override fun toString() = value.toString()

    companion object {
        val ZERO = Constant(0)
        val ONE = Constant(1)

        fun of(value: Int) = when (value) {
            0 -> ZERO
            1 -> ONE
            else -> Constant(value)
        }
    }
}
