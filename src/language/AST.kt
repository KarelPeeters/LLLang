package language

sealed class ASTNode(val position: SourcePosition) {
    abstract fun ASTRenderer.render()
}

sealed class Statement(position: SourcePosition) : ASTNode(position)

class IfStatement(
        position: SourcePosition,
        val condition: Expression,
        val thenBlock: Block,
        val elseBlock: Block?
) : Statement(position) {
    override fun ASTRenderer.render() {
        print("if ("); print((condition)); print(")")
        print(thenBlock)
        if (elseBlock != null) {
            print("else"); print(elseBlock)
        }
    }
}

class WhileStatement(
        position: SourcePosition,
        val condition: Expression,
        val block: Block
) : Statement(position) {
    override fun ASTRenderer.render() {
        print("while ("); print(condition); print(")")
        print(block)
    }
}

sealed class Expression(position: SourcePosition) : Statement(position)

class Block(
        position: SourcePosition,
        val statements: List<Statement>
) : ASTNode(position) {
    override fun ASTRenderer.render() {
        println("{"); nest().apply { statements.forEach { print(it); println(";") } }; print("}")
    }
}

class ExpressionList(
        position: SourcePosition,
        val expressions: List<Expression>
) : ASTNode(position) {
    override fun ASTRenderer.render() {
        expressions.forEachIndexed { i, exp -> if (i != 0) print(", "); print(exp) }
    }
}

class Assignment(
        position: SourcePosition,
        val target: Expression,
        val value: Expression
) : Expression(position) {
    override fun ASTRenderer.render() {
        print(target); print(" = "); print(value)
    }
}

private fun ASTRenderer.safePrint(exp: Expression) {
    if (exp is BinaryOp || exp is UnaryOp) {
        print("("); print(exp); print(")")
    } else print(exp)
}

class BinaryOp(
        position: SourcePosition,
        val type: Type,
        val left: Expression,
        val right: Expression
) : Expression(position) {
    override fun ASTRenderer.render() {
        safePrint(left); print(" ${type.symbol} "); safePrint(right)
    }

    enum class Type(val symbol: String) {
        Power("**"),
        Multiply("*"), Divide("/"), Modulus("%"),
        Add("+"), Subtract("-"),
        LT("<"), GT(">"), LTE("<="), GTE(">="),
        EQ("=="), NEQ("!="),
        BAnd("&&"), Iand("&"),
        BOr("||"), Ior("|");
    }
}

class UnaryOp(
        position: SourcePosition,
        val type: Type,
        val value: Expression
) : Expression(position) {
    override fun ASTRenderer.render() {
        if (type == Type.PostInc || type == Type.PostDec) {
            safePrint(value); print(type.symbol)
        } else {
            print(type.symbol); safePrint(value)
        }
    }

    enum class Type(val symbol: String) {
        Positive("+"), Negative("-"),
        PreInc("++"), PreDec("--"),
        PostInc("++"), PostDec("--"),
        BNot("!"), INot("~"),
    }
}

class Call(
        position: SourcePosition,
        val target: Expression,
        val arguments: ExpressionList
) : Expression(position) {
    override fun ASTRenderer.render() {
        safePrint(target); print("("); print(arguments); print(")")
    }
}

class Index(
        position: SourcePosition,
        val target: Expression,
        val index: Expression
) : Expression(position) {
    override fun ASTRenderer.render() {
        print(target); print("["); print(index); print("]")
    }
}

class IdentifierExpression(
        position: SourcePosition,
        val identifier: String
) : Expression(position) {
    override fun ASTRenderer.render() = print(identifier)
}

class BooleanLiteral(
        position: SourcePosition,
        val value: Boolean
) : Expression(position) {
    override fun ASTRenderer.render() = print(value.toString())
}

class NumberLiteral(
        position: SourcePosition,
        val value: String
) : Expression(position) {
    override fun ASTRenderer.render() = print(value)
}

class BreakStatement(
        position: SourcePosition
) : Statement(position) {
    override fun ASTRenderer.render() = print("break")
}

class ContinueStatement(
        position: SourcePosition
) : Statement(position) {
    override fun ASTRenderer.render() = print("continue")
}

class Declaration(
        position: SourcePosition,
        val identifier: String,
        val type: Type?,
        val value: Expression
) : Statement(position) {
    override fun ASTRenderer.render() {
        print("var $identifier")
        if (type != null) print(": ${type.str}")
        print(" = "); print(value)
    }
}

class Type(
        position: SourcePosition,
        val str: String
)

class ASTRenderer(private val builder: StringBuilder, private val indent: Int) {
    private var inLine = false

    fun print(str: String) {
        if (!inLine)
            builder.append(" ".repeat((4 * indent)))
        inLine = true
        builder.append(str)
    }

    fun print(astNode: ASTNode) {
        astNode.apply { render() }
    }

    fun println(str: String) {
        print(str)
        builder.append('\n')
        inLine = false
    }

    fun println(astNode: ASTNode) {
        print(astNode)
        builder.append('\n')
        inLine = false
    }

    fun nest() = ASTRenderer(builder, indent + 1)
}