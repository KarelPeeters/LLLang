package language.ir

import language.ir.ComparisonOpType.*
import language.ir.IntegerType.Companion.bool
import language.ir.IntegerType.Companion.i32
import language.ir.UnaryOpType.Neg
import language.ir.UnaryOpType.Not

sealed class BinaryOpType(val symbol: String) {
    val name = this::class.java.simpleName.toLowerCase()

    abstract fun returnType(leftType: Type, rightType: Type): Type

    abstract fun calculate(left: Constant, right: Constant): Constant

    override fun toString(): String = name
}

abstract class ArithmeticOpType(symbol: String, private val calc: (Int, Int) -> Int) : BinaryOpType(symbol) {
    override fun returnType(leftType: Type, rightType: Type): Type {
        require(leftType == rightType) { "$leftType != $rightType" }
        require(leftType is IntegerType) { "$leftType is not ${IntegerType::class.java.simpleName}" }
        return leftType
    }

    override fun calculate(left: Constant, right: Constant): Constant {
        val type = returnType(left.type, right.type)
        val value = calc(left.value, right.value)
        return Constant(type, value)
    }

    object Add : ArithmeticOpType("+", Int::plus)
    object Sub : ArithmeticOpType("-", Int::minus)
    object Mul : ArithmeticOpType("*", Int::times)
    object Div : ArithmeticOpType("/", Int::div)
    object Mod : ArithmeticOpType("%", Int::rem)

    object And : ArithmeticOpType("&", Int::and)
    object Or : ArithmeticOpType("|", Int::or)
}

sealed class ComparisonOpType(
        symbol: String,
        private val calc: (Int, Int) -> Boolean
) : BinaryOpType(symbol) {
    override fun returnType(leftType: Type, rightType: Type): Type {
        require(leftType == rightType) { "$leftType != $rightType" }
        require(leftType is IntegerType) { "$leftType is not an integer" }
        return bool
    }

    override fun calculate(left: Constant, right: Constant): Constant {
        val type = returnType(left.type, right.type)
        val value = calc(left.value, right.value)
        return Constant(type, if (value) 1 else 0)
    }

    object LT : ComparisonOpType("<", { a, b -> a < b })
    object GT : ComparisonOpType(">", { a, b -> a > b })
    object LTE : ComparisonOpType("<=", { a, b -> a <= b })
    object GTE : ComparisonOpType(">=", { a, b -> a >= b })

    object EQ : ComparisonOpType("==", { a, b -> a == b })
    object NEQ : ComparisonOpType("!=", { a, b -> a != b })
}

sealed class UnaryOpType(val symbol: String) {
    val name = this::class.java.simpleName.toLowerCase()

    abstract fun calculate(value: Constant): Constant

    override fun toString(): String = name

    object Neg : UnaryOpType("-") {
        override fun calculate(value: Constant) = when (value.type) {
            i32 -> Constant(i32, -value.value)
            bool -> {
                check(value.value == 0 || value.value == 1)
                Constant(bool, 1 - value.value)
            }
            else -> error("Unexpected type ${value.type}")
        }
    }

    object Not : UnaryOpType("!") {
        override fun calculate(value: Constant): Constant {
            require(value.type is IntegerType)
            return Constant(value.type, value.value.inv() and lowerMask(value.type.width))
        }
    }
}

private fun lowerMask(width: Int): Int {
    require(width in 0..Int.SIZE_BITS)
    return if (width == Int.SIZE_BITS)
        -1
    else
        (1 shl width) - 1
}

val AITHMETIC_OP_TYPES = listOf(ArithmeticOpType.Add, ArithmeticOpType.Sub, ArithmeticOpType.Mul, ArithmeticOpType.Div, ArithmeticOpType.Mod, ArithmeticOpType.And, ArithmeticOpType.Or)
val COMPARISON_OP_TYPES = listOf(LT, GT, LTE, GTE, EQ, NEQ)
val BINARY_OP_TYPES = AITHMETIC_OP_TYPES + COMPARISON_OP_TYPES

val UNARY_OP_TYPES = listOf(Neg, Not)
