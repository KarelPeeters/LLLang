package language.ir

import language.ir.IntegerType.Companion.bool

sealed class BinaryOpType(val symbol: String) {
    val name = this::class.java.simpleName.toLowerCase()

    abstract fun returnType(leftType: Type, rightType: Type): IntegerType

    abstract fun calculate(left: IntegerConstant, right: IntegerConstant): IntegerConstant

    override fun toString(): String = name
}

abstract class ArithmeticOpType(symbol: String, private val calc: (Int, Int) -> Int) : BinaryOpType(symbol) {
    override fun returnType(leftType: Type, rightType: Type): IntegerType {
        require(leftType is IntegerType) { "$leftType !is IntegerType" }
        require(rightType is IntegerType) { "$rightType !is IntegerType" }
        require(leftType == rightType) { "$leftType != $rightType" }
        return leftType
    }

    override fun calculate(left: IntegerConstant, right: IntegerConstant): IntegerConstant {
        val type = returnType(left.type, right.type)
        val value = calc(left.value, right.value)
        return IntegerConstant(type, value)
    }

    object Add : ArithmeticOpType("+", Int::plus)
    object Sub : ArithmeticOpType("-", Int::minus)
    object Mul : ArithmeticOpType("*", Int::times)
    object Div : ArithmeticOpType("/", Int::div)
    object Mod : ArithmeticOpType("%", Int::rem)

    object And : ArithmeticOpType("&", Int::and)
    object Or : ArithmeticOpType("|", Int::or)
    object Xor : ArithmeticOpType("^", Int::or)
}

sealed class ComparisonOpType(
        symbol: String,
        private val calc: (Int, Int) -> Boolean
) : BinaryOpType(symbol) {
    override fun returnType(leftType: Type, rightType: Type): IntegerType {
        require(leftType is IntegerType) { "$leftType !is IntegerType" }
        require(rightType is IntegerType) { "$rightType !is IntegerType" }
        require(leftType == rightType) { "$leftType != $rightType" }
        return bool
    }

    override fun calculate(left: IntegerConstant, right: IntegerConstant): IntegerConstant {
        val type = returnType(left.type, right.type)
        val value = calc(left.value, right.value)
        return IntegerConstant(type, if (value) 1 else 0)
    }

    object LT : ComparisonOpType("<", { a, b -> a < b })
    object GT : ComparisonOpType(">", { a, b -> a > b })
    object LTE : ComparisonOpType("<=", { a, b -> a <= b })
    object GTE : ComparisonOpType(">=", { a, b -> a >= b })

    object EQ : ComparisonOpType("==", { a, b -> a == b })
    object NEQ : ComparisonOpType("!=", { a, b -> a != b })
}
