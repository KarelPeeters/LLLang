package language.frontend

import language.ir.Alloc
import language.ir.BasicBlock
import language.ir.Blur
import language.ir.Branch
import language.ir.Constant
import language.ir.Eat
import language.ir.Exit
import language.ir.IntegerType.Companion.bool
import language.ir.IntegerType.Companion.i32
import language.ir.Jump
import language.ir.Load
import language.ir.Program
import language.ir.Store
import language.ir.Value
import java.util.*

private class Variable(val name: String, val pointer: Value) {
    override fun toString() = "[$name]"
}

private class LoopBlocks(val header: BasicBlock, val end: BasicBlock)

class Flattener {
    val program = Program()
    private val allocs = mutableListOf<Alloc>()
    private val loopBlockStack = LinkedList<LoopBlocks>()

    fun newBlock(name: String? = null) = BasicBlock(name).also { TODO() /*body.append(it)*/ }

    private inner class Context(val parent: Context?) {
        private val vars = mutableMapOf<String, Variable>()

        fun register(pos: SourcePosition, name: String, variable: Variable) {
            if (find(name) != null) throw DuplicateDeclarationException(pos, name)

            vars[name] = variable
        }

        fun find(name: String): Variable? = vars[name] ?: parent?.find(name)

        fun nest() = Context(this)
    }

    fun flatten(block: CodeBlock) {
        val entry = newBlock()
        TODO()
        //body.entry = entry
        val end = entry.appendNestedBlock(Context(null), block)
        end?.terminator = Exit

        allocs.asReversed().forEach { entry.insertAt(0, it) }
    }

    private fun BasicBlock.appendNestedBlock(context: Context, block: CodeBlock): BasicBlock? {
        val nested = context.nest()
        return block.statements.fold(this) { accBlock: BasicBlock?, statement ->
            accBlock?.appendStatement(nested, statement)
        }
    }

    private fun BasicBlock.appendStatement(context: Context, stmt: Statement): BasicBlock? = when (stmt) {
        is Expression -> appendExpression(context, stmt).first
        is Declaration -> {
            val type = when (stmt.type?.str) {
                "bool" -> bool
                "i32", null -> i32
                else -> throw IllegalTypeException(stmt.type.position, stmt.type.str)
            }
            val alloc = Alloc(stmt.identifier, type)
            allocs += alloc

            val variable = Variable(stmt.identifier, alloc)
            context.register(stmt.position, stmt.identifier, variable)

            val (next, value) = appendExpression(context, stmt.value)
            append(Store(variable.pointer, value))
            next
        }
        is CodeBlock -> appendNestedBlock(context, stmt)
        is IfStatement -> {
            val (afterCond, condValue) = this.appendExpression(context, stmt.condition)

            val thenBlock = newBlock("if.then")
            val thenEnd = thenBlock.appendNestedBlock(context, stmt.thenBlock)

            val elseBlock = newBlock("if.else")
            val elseEnd = if (stmt.elseBlock != null)
                elseBlock.appendNestedBlock(context, stmt.elseBlock)
            else
                elseBlock

            val end = newBlock("if.end")

            afterCond.terminator = Branch(condValue, thenBlock, elseBlock)
            thenEnd?.terminator = Jump(end)
            elseEnd?.terminator = Jump(end)

            end
        }
        is WhileStatement -> {
            val condBlock = newBlock("while.cond")
            val (afterCond, condValue) = condBlock.appendExpression(context, stmt.condition)

            val bodyBlock = newBlock("while.body")
            val endBlock = BasicBlock("while.end")

            val blocks = LoopBlocks(condBlock, endBlock)

            loopBlockStack.push(blocks)
            val bodyEnd = bodyBlock.appendNestedBlock(context, stmt.block)
            loopBlockStack.pop()

            TODO()
            //body.append(endBlock)

            this.terminator = Jump(condBlock)
            afterCond.terminator = Branch(condValue, bodyBlock, endBlock)
            bodyEnd?.terminator = Jump(condBlock)
            endBlock
        }
        is BreakStatement -> {
            terminator = Jump(loopBlockStack.peek().end)
            null
        }
        is ContinueStatement -> {
            terminator = Jump(loopBlockStack.peek().header)
            null
        }
        is ReturnStatement -> TODO("return")
    }

    private fun BasicBlock.appendExpression(context: Context, exp: Expression): Pair<BasicBlock, Value> = when (exp) {
        is NumberLiteral -> {
            this to Constant(i32, exp.value.toInt())
        }
        is BooleanLiteral -> {
            this to Constant(bool, if (exp.value) 1 else 0)
        }
        is IdentifierExpression -> {
            val variable = context.find(exp.identifier)
                           ?: throw IdNotFoundException(exp.position, exp.identifier)
            this to Load(null, variable.pointer).also { append(it) }
        }
        is Assignment -> {
            if (exp.target !is IdentifierExpression) TODO("other target types")
            val assignTarget = context.find(exp.target.identifier)
                               ?: throw IdNotFoundException(exp.target.position, exp.target.identifier)
            val (next, value) = appendExpression(context, exp.value)
            next.append(Store(assignTarget.pointer, value))
            next to value
        }
        is BinaryOp -> {
            val (afterLeft, leftValue) = appendExpression(context, exp.left)
            val (afterRight, rightValue) = afterLeft.appendExpression(context, exp.right)
            val result = language.ir.BinaryOp(null, exp.type, leftValue, rightValue)
            afterRight.append(result)
            afterRight to result
        }
        is UnaryOp -> {
            val (afterValue, value) = appendExpression(context, exp.value)
            val result = language.ir.UnaryOp(null, exp.type, value)
            afterValue.append(result)
            afterValue to result
        }
        is Call -> {
            if (exp.target is IdentifierExpression) {
                when (exp.target.identifier) {
                    "eat" -> {
                        val result = Eat()
                        val after = exp.arguments.fold(this) { before, operand ->
                            val (after, value) = before.appendExpression(context, operand)
                            result.addOperand(value)
                            after
                        }
                        after.append(result)
                        after to result
                    }
                    "blur" -> {
                        require(exp.arguments.size == 1) { "blur takes a single argument" }
                        val (after, value) = appendExpression(context, exp.arguments.first())
                        val result = Blur(value)
                        after.append(result)
                        after to result
                    }
                    else -> TODO("calls")
                }
            } else {
                TODO("dynamic calls")
            }
        }
        is Index -> TODO("index")
    }
}

class IdNotFoundException(pos: SourcePosition, identifier: String)
    : Exception("$pos: '$identifier' not found")

class DuplicateDeclarationException(pos: SourcePosition, identifier: String)
    : Exception("$pos: '$identifier' was already declared")

class IllegalTypeException(pos: SourcePosition, type: String)
    : Exception("$pos: Illegal type '$type'")
