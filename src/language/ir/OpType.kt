package language.ir

import language.ir.IntegerType.Companion.i32

sealed class BinaryOpType(val symbol: String) {
    abstract fun returnType(leftType: Type, rightType: Type): Type

    abstract fun calculate(left: Constant, right: Constant): Constant

    override fun toString(): String = this::class.java.simpleName.toLowerCase()
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

abstract class ComparisonOpType(
        symbol: String,
        private val calc: (Int, Int) -> Boolean,
        private val requireInteger: Boolean
) : BinaryOpType(symbol) {

    override fun returnType(leftType: Type, rightType: Type): Type {
        require(leftType == rightType) { "$leftType != $rightType" }
        if (requireInteger)
            require(leftType is IntegerType) { "$leftType is not ${IntegerType::class.java.simpleName}" }
        return IntegerType.bool
    }

    override fun calculate(left: Constant, right: Constant): Constant {
        val type = returnType(left.type, right.type)
        val value = calc(left.value, right.value)
        return Constant(type, if (value) 1 else 0)
    }

    object LT : ComparisonOpType("<", { a, b -> a < b }, true)
    object GT : ComparisonOpType(">", { a, b -> a > b }, true)
    object LTE : ComparisonOpType("<=", { a, b -> a <= b }, true)
    object GTE : ComparisonOpType(">=", { a, b -> a >= b }, true)

    object EQ : ComparisonOpType("==", { a, b -> a == b }, true)
    object NEQ : ComparisonOpType("!=", { a, b -> a != b }, true)
}

sealed class UnaryOpType(val symbol: String) {
    override fun toString(): String = this::class.java.simpleName.toLowerCase()

    abstract fun calculate(value: Constant): Constant

    object Neg : UnaryOpType("-") {
        override fun calculate(value: Constant): Constant {
            require(value.type == i32)
            return Constant(i32, -value.value)
        }
    }

    object Not : UnaryOpType("!") {
        override fun calculate(value: Constant): Constant {
            require(value.type is IntegerType)
            return Constant(value.type, value.value.inv() and lowerMask(value.type.width))
        }
    }

    //PreInc("++"), PreDec("--"),
    //-PostInc("++"), PostDec("--"),
}

private fun lowerMask(width: Int): Int {
    require(width in 0..Int.SIZE_BITS)
    return if (width == Int.SIZE_BITS)
        -1
    else
        (1 shl width) - 1
}