package language.ir

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

abstract class ComparisonOpType(symbol: String, private val calc: (Int, Int) -> Boolean) : BinaryOpType(symbol) {
    override fun returnType(leftType: Type, rightType: Type): Type {
        require(leftType == rightType) { "$leftType != $rightType" }
        return IntegerType.bool
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