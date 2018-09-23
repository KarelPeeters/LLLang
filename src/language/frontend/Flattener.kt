package language.frontend

import language.ir.Alloc
import language.ir.BasicBlock
import language.ir.Function
import language.ir.Branch
import language.ir.Constant
import language.ir.Exit
import language.ir.Jump
import language.ir.Load
import language.ir.Store
import language.ir.Value

class Variable(val name: String, val pointer: Value) {
    override fun toString() = "[$name]"
}

open class AbstractFlattener {
    val body = Function()

    private var nextVarId = 0
    private var nextBlockId = 0

    fun genVarName() = (nextVarId++).toString()

    fun newBlock(name: String? = null): BasicBlock {
        val block = BasicBlock(name ?: nextBlockId.toString())
        body.push(block)
        nextBlockId++
        return block
    }
}

class Flattener : AbstractFlattener() {
    val allocs = mutableListOf<Alloc>()

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
        end.terminator = Exit

        allocs.asReversed().forEach { new.insertAt(0, it) }
    }

    private fun BasicBlock.appendNestedBlock(context: Context, block: CodeBlock): BasicBlock {
        val nested = context.nest()
        return block.statements.fold(this) { accBlock, statement ->
            accBlock.appendStatement(nested, statement)
        }
    }


    private fun BasicBlock.appendStatement(context: Context, stmt: Statement): BasicBlock = when (stmt) {
        is Expression -> appendExpression(context, stmt).first
        is Declaration -> {
            val alloc = Alloc(stmt.identifier)
            allocs += alloc

            val variable = Variable(stmt.identifier, alloc)
            context.register(stmt.identifier, variable)

            val (next, value) = appendExpression(context, stmt.value)
            append(Store(variable.pointer, value))
            next
        }
        is CodeBlock -> appendNestedBlock(context, stmt)
        is IfStatement -> {
            val (afterCond, condValue) = this.appendExpression(context, stmt.condition)

            val thenBlock = newBlock()
            val thenEnd = thenBlock.appendNestedBlock(context, stmt.thenBlock)

            val elseBlock = newBlock()
            val elseEnd = if (stmt.elseBlock != null)
                elseBlock.appendNestedBlock(context, stmt.elseBlock)
            else
                elseBlock

            val end = newBlock()

            afterCond.terminator = Branch(condValue, thenBlock, elseBlock)
            thenEnd.terminator = Jump(end)
            elseEnd.terminator = Jump(end)

            end
        }
        is WhileStatement -> {
            val condBlock = newBlock()
            val (afterCond, condValue) = condBlock.appendExpression(context, stmt.condition)

            val bodyBlock = newBlock()
            val bodyEnd = bodyBlock.appendNestedBlock(context, stmt.block)

            val endBlock = newBlock()

            this.terminator = Jump(condBlock)
            afterCond.terminator = Branch(condValue, bodyBlock, endBlock)
            bodyEnd.terminator = Jump(condBlock)
            endBlock
        }
        is BreakStatement -> TODO()
        is ContinueStatement -> TODO()
    }

    private fun BasicBlock.appendExpression(context: Context, exp: Expression): Pair<BasicBlock, Value> = when (exp) {
        is NumberLiteral -> {
            this to Constant.of(exp.value.toInt())
        }
        is BooleanLiteral -> {
            this to Constant.of(if (exp.value) 1 else 0)
        }
        is IdentifierExpression -> {
            val variable = context.find(exp.identifier)
                    ?: throw IdNotFoundException(exp.position, exp.identifier)
            this to append(Load(genVarName(), variable.pointer))
        }
        is Assignment -> {
            if (exp.target !is IdentifierExpression) TODO("other target types")
            val assignTarget = context.find(exp.target.identifier)
                    ?: throw IdNotFoundException(exp.target.position, exp.target.identifier)
            val (next, value) = appendExpression(context, exp.value)
            append(Store(assignTarget.pointer, value))
            next to value
        }
        is BinaryOp -> {
            val (afterLeft, leftValue) = appendExpression(context, exp.left)
            val (afterRight, rightValue) = afterLeft.appendExpression(context, exp.right)
            val result = language.ir.BinaryOp(genVarName(), exp.type, leftValue, rightValue)
            afterRight to append(result)
        }
        is UnaryOp -> TODO("unary")
        is Call -> TODO("calls")
        is Index -> TODO("index")
    }
}

class IdNotFoundException(pos: SourcePosition, identifier: String)
    : Exception("'$identifier' not found at $pos")

class DuplicateDeclarationException(identifier: String)
    : Exception("'$identifier' was already declared")
