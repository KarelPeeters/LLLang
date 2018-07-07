package language.mid

import language.high.*

class Variable(val id: Int) {
    override fun toString() = "[$id]"
}

sealed class OpCode {
    class Set(
            val target: Variable,
            val value: Int
    ) : OpCode() {
        override fun toString() = "$target <- $value"
    }

    class Copy(
            val target: Variable,
            val value: Variable
    ) : OpCode() {
        override fun toString() = "$target <- $value"
    }

    class Binary(
            val target: Variable,
            val operation: BinaryOp.Type,
            val left: Variable,
            val right: Variable
    ) : OpCode() {
        override fun toString() = "$target <- ($left) ${operation.symbol} ($right)"
    }

    class Goto(
            val next: Block,
            val condition: Variable
    ) : OpCode() {
        override fun toString() = "goto {${next.id}} $condition"
    }
}

interface Block {
    val id: Int
    val content: List<OpCode>
    val next: Block?
}

class MutableBlock(
        override var id: Int
) : Block {
    override val content = mutableListOf<OpCode>()
    override var next: Block? = null

    fun push(opCode: OpCode) {
        content += opCode
    }

    override fun toString() = "Block <$id>:\n" + content.joinToString("\n") + "\ngoto <${next?.id}>"
}

open class AbstractFlattener {
    private var nextVarId = 0
    private var nextBlockId = 0

    fun newVar() = Variable(nextVarId++).also { vars += it }
    fun newBlock() = MutableBlock(nextBlockId++).also { blocks += it }

    val vars = mutableListOf<Variable>()
    val blocks = mutableListOf<MutableBlock>()
}

class Flattener : AbstractFlattener() {
    inner class Context(val parent: Context?) {
        private val vars = mutableMapOf<String, Variable>()

        fun register(name: String, variable: Variable) { vars[name] = variable }
        fun find(name: String): Variable? = vars[name] ?: parent?.find(name)

        fun nest() = Context(this)
    }

    fun flatten(block: CodeBlock) {
        newBlock().appendBlock(Context(null), block)
    }

    private fun MutableBlock.appendBlock(context: Context, block: CodeBlock) =
            block.statements.fold(this) { accBlock, statement -> accBlock.appendStatement(context, statement) }

    private fun MutableBlock.appendStatement(context: Context, stmt: Statement): MutableBlock = when (stmt) {
        is Expression -> appendExpression(context, stmt, null)
        is Declaration -> {
            val target = newVar()
            appendExpression(context, stmt.value, target)
            context.register(stmt.identifier, target)
            this
        }
        is CodeBlock -> appendBlock(context.nest(), stmt)
        is IfStatement -> TODO()
        is WhileStatement -> TODO()
        is BreakStatement -> TODO()
        is ContinueStatement -> TODO()
    }

    private fun MutableBlock.appendExpression(context: Context, exp: Expression, target: Variable?): MutableBlock {
        when (exp) {
            is NumberLiteral -> push(OpCode.Set(target ?: return this, exp.value.toInt()))
            is BooleanLiteral -> push(OpCode.Set(target ?: return this, if (exp.value) 1 else 0))
            is IdentifierExpression -> push(OpCode.Copy(target ?: return this, context.find(exp.identifier)
                    ?: throw IdNotFoundException(exp.position, exp.identifier)))
            is Assignment -> {
                if (exp.target !is IdentifierExpression) TODO("other target types")
                val valueVar = context.find(exp.target.identifier) ?: throw IdNotFoundException(exp.target.position, exp.target.identifier)
                appendExpression(context, exp.value, valueVar)
                push(OpCode.Copy(target ?: return this, valueVar))
            }
            is BinaryOp -> {
                if (target == null) {
                    appendExpression(context, exp.left, null)
                    appendExpression(context, exp.right, null)
                } else {
                    val leftVar = newVar(); appendExpression(context, exp.left, leftVar)
                    val rightVar = newVar(); appendExpression(context, exp.right, rightVar)
                    push(OpCode.Binary(target, exp.type, leftVar, rightVar))
                }
            }
            is UnaryOp -> TODO()
            is Call -> TODO()
            is Index -> TODO()
        }
        return this
    }
}

class IdNotFoundException(pos: SourcePosition, identifier: String) : Exception("'$identifier' not found at $pos")