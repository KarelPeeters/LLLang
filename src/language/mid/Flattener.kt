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
        override fun toString() = "$target <- $left ${operation.symbol} $right"
    }
}

sealed class Goto {
    object End : Goto() {
        override fun toString() = "<end>"
    }

    class Always(val next: Block) : Goto() {
        override fun toString() = "goto <${next.id}>"

    }

    class Split(val onTrue: Block, val onFalse: Block, val condition: Variable) : Goto() {
        override fun toString() = "goto if $condition <${onTrue.id}> else <${onFalse.id}>"
    }
}

interface Block {
    val id: Int
    val content: List<OpCode>
    val goto: Goto
}

class MutableBlock(
        override var id: Int
) : Block {
    override val content = mutableListOf<OpCode>()
    override lateinit var goto: Goto

    fun push(opCode: OpCode) {
        content += opCode
    }

    override fun toString() = "Block <$id>:\n" + content.joinToString(separator = "") { "$it\n" } + "$goto"
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

        fun register(name: String, variable: Variable) {
            if (find(name) != null) throw DuplicateDeclarationException(name)

            vars[name] = variable
        }

        fun find(name: String): Variable? = vars[name] ?: parent?.find(name)

        fun nest() = Context(this)
    }

    fun flatten(block: CodeBlock) {
        val new = newBlock()
        val end = new.appendNestedBlock(Context(null), block)
        end.goto = Goto.End
    }

    private fun MutableBlock.appendNestedBlock(context: Context, block: CodeBlock): MutableBlock {
        val nested = context.nest()
        return block.statements.fold(this) { accBlock, statement ->
            accBlock.appendStatement(nested, statement)
        }
    }


    private fun MutableBlock.appendStatement(context: Context, stmt: Statement): MutableBlock = when (stmt) {
        is Expression -> appendExpression(context, stmt, null)
        is Declaration -> {
            val target = newVar()
            val next = appendExpression(context, stmt.value, target)
            context.register(stmt.identifier, target)
            next
        }
        is CodeBlock -> appendNestedBlock(context, stmt)
        is IfStatement -> {
            val condVar = newVar()
            val afterCond = this.appendExpression(context, stmt.condition, condVar)

            val thenBlock = newBlock()
            val thenEnd = thenBlock.appendNestedBlock(context, stmt.thenBlock)

            val elseBlock = newBlock()
            val elseEnd = if (stmt.elseBlock != null)
                elseBlock.appendNestedBlock(context, stmt.elseBlock)
            else
                elseBlock

            val end = newBlock()

            afterCond.goto = Goto.Split(thenBlock, elseBlock, condVar)
            thenEnd.goto = Goto.Always(end)
            elseEnd.goto = Goto.Always(end)

            end
        }
        is WhileStatement -> {
            val condBlock = newBlock()
            val condVar = newVar()
            val afterCond = condBlock.appendExpression(context, stmt.condition, condVar)

            val bodyBlock = newBlock()
            val bodyEnd = bodyBlock.appendNestedBlock(context, stmt.block)

            val endBlock = newBlock()

            this.goto = Goto.Always(condBlock)
            afterCond.goto = Goto.Split(bodyBlock, endBlock, condVar)
            bodyEnd.goto = Goto.Always(this)
            endBlock
        }
        is BreakStatement -> TODO()
        is ContinueStatement -> TODO()
    }

    private fun MutableBlock.appendExpression(context: Context, exp: Expression, target: Variable?): MutableBlock = when (exp) {
        is NumberLiteral -> {
            if (target != null)
                push(OpCode.Set(target, exp.value.toInt()))
            this
        }
        is BooleanLiteral -> {
            if (target != null)
                push(OpCode.Set(target, if (exp.value) 1 else 0))
            this
        }
        is IdentifierExpression -> {
            if (target != null)
                push(OpCode.Copy(target, context.find(exp.identifier)
                        ?: throw IdNotFoundException(exp.position, exp.identifier)))
            this
        }
        is Assignment -> {
            if (exp.target !is IdentifierExpression) TODO("other target types")
            val assignTargetVar = context.find(exp.target.identifier)
                    ?: throw IdNotFoundException(exp.target.position, exp.target.identifier)
            appendExpression(context, exp.value, assignTargetVar).apply {
                if (target != null)
                    push(OpCode.Copy(target, assignTargetVar))
            }
        }
        is BinaryOp -> {
            if (target == null) {
                val afterLeft = this.appendExpression(context, exp.left, null)
                afterLeft.appendExpression(context, exp.right, null)
            } else {
                val leftVar = newVar()
                val afterLeft = appendExpression(context, exp.left, leftVar)
                val rightVar = newVar()
                val afterRight = afterLeft.appendExpression(context, exp.right, rightVar)
                afterRight.push(OpCode.Binary(target, exp.type, leftVar, rightVar))
                afterRight
            }
        }
        is UnaryOp -> TODO()
        is Call -> TODO()
        is Index -> TODO()
    }
}

class IdNotFoundException(pos: SourcePosition, identifier: String)
    : Exception("'$identifier' not found at $pos")

class DuplicateDeclarationException(identifier: String)
    : Exception("'$identifier' was already declared")