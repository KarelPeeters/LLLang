package language.ir

sealed class BinaryOpType(val symbol: String) {
    abstract fun returnType(leftType: Type, rightType: Type): Type

    override fun toString(): String = this::class.java.simpleName.toLowerCase()
}

abstract class ArithmeticOpType(symbol: String) : BinaryOpType(symbol) {
    override fun returnType(leftType: Type, rightType: Type): Type {
        require(leftType == rightType) { "$leftType != $rightType" }
        require(leftType is IntegerType) { "$leftType is not ${IntegerType::class.java.simpleName}" }
        return leftType
    }

    object Add : ArithmeticOpType("+")
    object Sub : ArithmeticOpType("-")
    object Mul : ArithmeticOpType("*")
    object Div : ArithmeticOpType("/")
    object Mod : ArithmeticOpType("%")

    object And : ArithmeticOpType("&")
    object Or : ArithmeticOpType("|")
}

abstract class ComparisonOpType(symbol: String) : BinaryOpType(symbol) {
    override fun returnType(leftType: Type, rightType: Type): Type {
        require(leftType == rightType) { "$leftType != $rightType" }
        return IntegerType.bool
    }

    object LT : ComparisonOpType("<")
    object GT : ComparisonOpType(">")
    object LTE : ComparisonOpType("<=")
    object GTE : ComparisonOpType(">=")
    object EQ : ComparisonOpType("==")
    object NEQ : ComparisonOpType("!=")
}