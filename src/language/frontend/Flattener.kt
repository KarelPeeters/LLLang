package language.frontend

import language.ir.Alloc
import language.ir.BasicBlock
import language.ir.Body
import language.ir.Branch
import language.ir.Constant
import language.ir.Exit
import language.ir.Jump
import language.ir.Load
import language.ir.Store

class Variable(val name: String, val pointer: Alloc) {
    override fun toString() = "[$name]"
}

open class AbstractFlattener {
    private var nextVarId = 0
    private var nextBlockId = 0

    fun genVarName() = (nextVarId++).toString()

    fun BasicBlock.newVar(name: String? = null): Variable {
        val usedName = name ?: genVarName()
        val alloc = Alloc(usedName)
        this.append(alloc)
        val variable = Variable(usedName, Alloc(usedName))
        vars += variable
        return variable
    }

    fun newBlock(name: String? = null): BasicBlock {
        val block = BasicBlock(name ?: nextBlockId.toString())
        body.push(block)
        nextBlockId++
        return block
    }

    val vars = mutableListOf<Variable>()
    val body = Body()
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
        end.terminator = Exit
    }

    private fun BasicBlock.appendNestedBlock(context: Context, block: CodeBlock): BasicBlock {
        val nested = context.nest()
        return block.statements.fold(this) { accBlock, statement ->
            accBlock.appendStatement(nested, statement)
        }
    }


    private fun BasicBlock.appendStatement(context: Context, stmt: Statement): BasicBlock = when (stmt) {
        is Expression -> appendExpression(context, stmt, newVar())
        is Declaration -> {
            val target = newVar(stmt.identifier)
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

            val condValue = Load(genVarName(), condVar.pointer)
            append(condValue)

            afterCond.terminator = Branch(condValue, thenBlock, elseBlock)
            thenEnd.terminator = Jump(end)
            elseEnd.terminator = Jump(end)

            end
        }
        is WhileStatement -> {
            val condBlock = newBlock()
            val condVar = newVar()
            val afterCond = condBlock.appendExpression(context, stmt.condition, condVar)

            val bodyBlock = newBlock()
            val bodyEnd = bodyBlock.appendNestedBlock(context, stmt.block)

            val endBlock = newBlock()

            this.terminator = Jump(condBlock)
            afterCond.terminator = Branch(condVar.pointer, bodyBlock, endBlock)
            bodyEnd.terminator = Jump(this)
            endBlock
        }
        is BreakStatement -> TODO()
        is ContinueStatement -> TODO()
    }

    private fun BasicBlock.appendExpression(context: Context, exp: Expression, target: Variable): BasicBlock = when (exp) {
        is NumberLiteral -> {
            append(Store(target.pointer, Constant.of(exp.value.toInt())))
            this
        }
        is BooleanLiteral -> {
            append(Store(target.pointer, Constant.of(if (exp.value) 1 else 0)))
            this
        }
        is IdentifierExpression -> {
            val variable = context.find(exp.identifier)
                    ?: throw IdNotFoundException(exp.position, exp.identifier)
            val read = Load(genVarName(), variable.pointer).also { append(it) }
            append(Store(target.pointer, read))
            this
        }
        is Assignment -> {
            if (exp.target !is IdentifierExpression) TODO("other target types")
            val assignTarget = context.find(exp.target.identifier)
                    ?: throw IdNotFoundException(exp.target.position, exp.target.identifier)
            val next = appendExpression(context, exp.value, assignTarget)
            val value = Load(genVarName(), assignTarget.pointer)
            append(Store(target.pointer, value))
            next
        }
        is BinaryOp -> {
            val leftVar = newVar()
            val afterLeft = appendExpression(context, exp.left, leftVar)
            val rightVar = newVar()
            val afterRight = afterLeft.appendExpression(context, exp.right, rightVar)
            val op = language.ir.BinaryOp(target.name, exp.type, leftVar.pointer, rightVar.pointer)
            append(Store(target.pointer, op))
            afterRight
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
