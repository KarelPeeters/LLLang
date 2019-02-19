package language.frontend

import language.ir.BinaryOpType
import language.ir.UnaryOpType

sealed class ASTNode(val position: SourcePosition) {
    abstract fun ASTRenderer.render()
}

class Program(
        position: SourcePosition,
        val toplevels: List<TopLevel>
) : ASTNode(position) {
    override fun ASTRenderer.render() {
        for (topLevel in toplevels) {
            println(topLevel)
        }
    }
}

sealed class TopLevel(
        position: SourcePosition
) : ASTNode(position)

class Function(
        position: SourcePosition,
        val name: String,
        val parameters: List<Parameter>,
        val retType: TypeAnnotation?,
        val body: FunctionBody
) : TopLevel(position) {
    override fun ASTRenderer.render() {
        print("fun $name(${parameters.joinToString { it.toString() }})")
        if (retType != null)
            print(": $retType")
        print(" ")
        when (body) {
            is FunctionBody.Block -> print(body.block)
            is FunctionBody.Expr -> {
                print("= ")
                print(body.exp)
            }
        }
    }

    sealed class FunctionBody {
        class Block(val block: CodeBlock) : FunctionBody()
        class Expr(val exp: Expression) : FunctionBody()
    }
}

class Parameter(
        position: SourcePosition,
        val name: String,
        val type: TypeAnnotation
) : ASTNode(position) {
    override fun toString() = "$name: $type"

    override fun ASTRenderer.render() {
        print("$name: $type")
    }
}

sealed class Statement(position: SourcePosition) : ASTNode(position)

class CodeBlock(
        position: SourcePosition,
        val statements: List<Statement>
) : Statement(position) {
    override fun ASTRenderer.render() {
        println("{"); nest().apply { statements.forEach { print(it); println(";") } }; print("}")
    }
}

class IfStatement(
        position: SourcePosition,
        val condition: Expression,
        val thenBlock: CodeBlock,
        val elseBlock: CodeBlock?
) : Statement(position) {
    override fun ASTRenderer.render() {
        print("if ("); print((condition)); print(") ")
        print(thenBlock)
        if (elseBlock != null) {
            print(" else "); print(elseBlock)
        }
    }
}

class WhileStatement(
        position: SourcePosition,
        val condition: Expression,
        val block: CodeBlock
) : Statement(position) {
    override fun ASTRenderer.render() {
        print("while ("); print(condition); print(") ")
        print(block)
    }
}

class ReturnStatement(
        position: SourcePosition,
        val value: Expression?
) : Statement(position) {
    override fun ASTRenderer.render() {
        print("return "); if (value != null) print(value)
    }
}


sealed class Expression(position: SourcePosition) : Statement(position)

class Assignment(
        position: SourcePosition,
        val target: Expression,
        val value: Expression
) : Expression(position) {
    override fun ASTRenderer.render() {
        safePrint(target); print(" = "); safePrint(value)
    }
}

private fun ASTRenderer.safePrint(exp: Expression) {
    if (exp is BinaryOp || exp is UnaryOp || exp is Assignment) {
        print("("); print(exp); print(")")
    } else print(exp)
}

class BinaryOp(
        position: SourcePosition,
        val type: BinaryOpType,
        val left: Expression,
        val right: Expression
) : Expression(position) {
    override fun ASTRenderer.render() {
        safePrint(left); print(" ${type.symbol} "); safePrint(right)
    }
}

class UnaryOp(
        position: SourcePosition,
        val type: UnaryOpType,
        val value: Expression
) : Expression(position) {
    override fun ASTRenderer.render() {
        /*if (type == Type.PostInc || type == Type.PostDec) {
            safePrint(value); print(type.symbol)
        } else*/ run {
            print(type.symbol); safePrint(value)
        }
    }
}

class Call(
        position: SourcePosition,
        val target: Expression,
        val arguments: List<Expression>
) : Expression(position) {
    override fun ASTRenderer.render() {
        safePrint(target); print("("); arguments.forEachIndexed { i, e -> if (i != 0) print(", "); print(e) }; print(")")
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
        val mutable: Boolean,
        val type: TypeAnnotation?,
        val value: Expression
) : Statement(position) {
    override fun ASTRenderer.render() {
        print(if (mutable) "var" else "val")
        print(" $identifier")
        if (type != null) print(": ${type.str}")
        print(" = "); print(value)
    }
}

class TypeAnnotation(
        position: SourcePosition,
        val str: String
) : ASTNode(position) {
    override fun ASTRenderer.render() = print(str)
    override fun toString() = str
}

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