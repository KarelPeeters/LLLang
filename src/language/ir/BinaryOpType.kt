package language.ir

enum class BinaryOpType(val symbol: String) {
    //Power("**"),
    Multiply("*"),
    Divide("/"), Modulus("%"),
    Add("+"), Subtract("-"),
    LT("<"), GT(">"), LTE("<="), GTE(">="),
    EQ("=="), NEQ("!="),
    BAnd("&&"), Iand("&"),
    BOr("||"), Ior("|");
}