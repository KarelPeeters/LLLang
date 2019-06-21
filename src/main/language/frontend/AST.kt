package language.frontend

import language.ir.BinaryOpType
import language.ir.UnaryOpType

abstract class ASTNode(val position: SourcePosition) {
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

class Struct(
        position: SourcePosition,
        val name: String,
        val properties: List<Parameter>,
        val functions: List<Function>
) : TopLevel(position) {
    override fun ASTRenderer.render() {
        print("struct $name("); printList(properties); print(")")
        if (functions.isNotEmpty()) {
            println(" {")
            nest().printList(functions, "\n\n")
            println()
            print("}")
        }
        println()
    }
}

class Function(
        position: SourcePosition,
        val name: String,
        val parameters: List<Parameter>,
        val retType: TypeAnnotation?,
        val body: FunctionBody
) : TopLevel(position) {
    override fun ASTRenderer.render() {
        print("fun $name("); printList(parameters); print(")")
        if (retType != null) {
            print(": "); print(retType)
        }
        print(" "); print(body)
    }

    sealed class FunctionBody(position: SourcePosition) : ASTNode(position) {
        class Block(position: SourcePosition, val block: CodeBlock) : FunctionBody(position) {
            override fun ASTRenderer.render() {
                print(block)
            }
        }

        class Expr(position: SourcePosition, val exp: Expression) : FunctionBody(position) {
            override fun ASTRenderer.render() {
                print("= ")
                print(exp)
            }
        }
    }
}

class Parameter(
        position: SourcePosition,
        val name: String,
        val type: TypeAnnotation
) : ASTNode(position) {
    override fun ASTRenderer.render() {
        print("$name: "); print(type)
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

class ArrayIndex(
        position: SourcePosition,
        val target: Expression,
        val index: Expression
) : Expression(position) {
    override fun ASTRenderer.render() {
        safePrint(target); print("["); print(index); print("]")
    }
}

class ArrayInitializer(
        position: SourcePosition,
        val values: List<Expression>
) : Expression(position) {
    override fun ASTRenderer.render() {
        print("["); printList(values); print("]")
    }
}

class DotIndex(
        position: SourcePosition,
        val target: Expression,
        val index: String
) : Expression(position) {
    override fun ASTRenderer.render() {
        safePrint(target); print("."); print(index)
    }
}

class IdentifierExpression(
        position: SourcePosition,
        val identifier: String
) : Expression(position) {
    override fun ASTRenderer.render() = print(identifier)
}

class ThisExpression(
        position: SourcePosition
) : Expression(position) {
    override fun ASTRenderer.render() = print("this")
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
        val value: Expression?
) : Statement(position) {
    override fun ASTRenderer.render() {
        print(if (mutable) "var" else "val")
        print(" $identifier")
        if (type != null) {
            print(": "); print(type)
        }
        if (value != null) {
            print(" = "); print(value)
        }
    }
}

sealed class TypeAnnotation(
        position: SourcePosition
) : ASTNode(position) {
    class Simple(
            position: SourcePosition,
            val str: String
    ) : TypeAnnotation(position) {
        override fun ASTRenderer.render() = print(str)

        override fun toString() = str
    }

    class Function(
            position: SourcePosition,
            val paramTypes: List<TypeAnnotation>,
            val returnType: TypeAnnotation
    ) : TypeAnnotation(position) {
        override fun ASTRenderer.render() {
            print("("); printList(paramTypes); print(") -> "); print(returnType)
        }

        override fun toString() =
                "(" + paramTypes.joinToString { it.toString() } + ") -> " + returnType
    }

    class Array(
            position: SourcePosition,
            val innerType: TypeAnnotation,
            val size: Int
    ) : TypeAnnotation(position) {
        override fun ASTRenderer.render() {
            print("["); print(innerType); print(", "); print(size.toString()); print("]")
        }

    }
}

class ASTRenderer(private val builder: StringBuilder, private val indent: String = "") {
    private var inLine = false

    fun print(str: String) {
        val cleaned = str.replace("\n", "\n$indent")

        if (!inLine)
            builder.append(indent)
        inLine = true
        builder.append(cleaned)
    }

    fun print(astNode: ASTNode) {
        astNode.apply { render() }
    }

    fun printList(list: List<ASTNode>, separator: String = ", ") {
        for ((i, node) in list.withIndex()) {
            if (i != 0) print(separator)
            print(node)
        }
    }

    fun println() {
        builder.append('\n')
        inLine = false
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

    fun nest() = ASTRenderer(builder, "$indent    ")
}