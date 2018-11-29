package language.frontend

import language.ir.Alloc
import language.ir.BasicBlock
import language.ir.Blur
import language.ir.Branch
import language.ir.Constant
import language.ir.Eat
import language.ir.IntegerType.Companion.bool
import language.ir.IntegerType.Companion.i32
import language.ir.Jump
import language.ir.Load
import language.ir.Return
import language.ir.Store
import language.ir.Type
import language.ir.UnitType
import language.ir.UnitValue
import language.ir.Value
import java.util.*
import language.ir.BinaryOp as IrBinaryOp
import language.ir.Function as IrFunction
import language.ir.Program as IrProgram
import language.ir.UnaryOp as IrUnaryOp

sealed class Variable(val name: String) {
    abstract fun loadValue(block: BasicBlock): Value
    abstract fun storeValue(block: BasicBlock, value: Value)

    class Memory(name: String, val pointer: Value) : Variable(name) {
        override fun loadValue(block: BasicBlock) = Load(name, pointer).also { block.append(it) }
        override fun storeValue(block: BasicBlock, value: Value) {
            block.append(Store(pointer, value))
        }
    }

    class Parameter(name: String, val value: Value) : Variable(name) {
        override fun loadValue(block: BasicBlock) = value
        override fun storeValue(block: BasicBlock, value: Value) =
                throw ParameterValueImmutableException(name)
    }
}

private class Scope(val parent: Scope?) {
    private val vars = mutableMapOf<String, Variable>()

    fun register(pos: SourcePosition, variable: Variable) {
        val name = variable.name
        if (find(name) != null) throw DuplicateDeclarationException(pos, name)
        vars[name] = variable
    }

    fun find(name: String): Variable? = vars[name] ?: parent?.find(name)

    fun nest() = Scope(this)
}

private class LoopBlocks(val header: BasicBlock, val end: BasicBlock)

class Flattener {
    val program = IrProgram()

    var functions = mutableMapOf<String, IrFunction>()
    private lateinit var currentFunction: IrFunction
    private val allocs = mutableListOf<Alloc>()
    private val loopBlockStack = LinkedList<LoopBlocks>()

    fun newBlock(name: String? = null) = BasicBlock(name).also { currentFunction.append(it) }

    fun flatten(astProgram: Program) {
        //build function map
        functions.clear()
        for (toplevel in astProgram.toplevels) {
            when (toplevel) {
                is Function -> {
                    if (toplevel.name in functions)
                        throw DuplicateFunctionDeclaration(toplevel.position, toplevel.name)
                    val irFunction = IrFunction(
                            toplevel.name,
                            toplevel.parameters.map { it.name to resolveType(it.type) },
                            resolveType(toplevel.retType, UnitType)
                    )
                    functions[toplevel.name] = irFunction
                    program.functions += irFunction
                    if (irFunction.name == "main")
                        program.entry = irFunction
                }
            }
        }

        //generate code
        val scope = Scope(null)
        for (toplevel in astProgram.toplevels) {
            when (toplevel) {
                is Function -> appendFunction(scope, toplevel, functions.getValue(toplevel.name))
            }
        }
    }

    private fun appendFunction(scope: Scope, function: Function, irFunction: IrFunction) {
        currentFunction = irFunction
        allocs.clear()
        require(loopBlockStack.isEmpty())

        val bodyScope = Scope(scope)
        for ((param, value) in function.parameters.zip(irFunction.parameters)) {
            bodyScope.register(param.position, Variable.Parameter(param.name, value))
        }

        val entry = newBlock("entry")
        irFunction.entry = entry
        val end = entry.appendNestedBlock(bodyScope, function.block)
        end?.terminator = Return(UnitValue)
        allocs.asReversed().forEach { entry.insertAt(0, it) }
    }

    private fun BasicBlock.appendNestedBlock(scope: Scope, block: CodeBlock): BasicBlock? {
        val nested = scope.nest()
        return block.statements.fold(this) { accBlock: BasicBlock?, statement ->
            accBlock?.appendStatement(nested, statement)
        }
    }

    private fun BasicBlock.appendStatement(scope: Scope, stmt: Statement): BasicBlock? = when (stmt) {
        is Expression -> appendExpression(scope, stmt).first
        is Declaration -> {
            val type = resolveType(stmt.type, i32)
            val alloc = Alloc(stmt.identifier, type)
            allocs += alloc

            val variable = Variable.Memory(stmt.identifier, alloc)
            scope.register(stmt.position, variable)

            val (next, value) = appendExpression(scope, stmt.value)
            variable.storeValue(next, value)
            next
        }
        is CodeBlock -> appendNestedBlock(scope, stmt)
        is IfStatement -> {
            val (afterCond, condValue) = this.appendExpression(scope, stmt.condition)

            val thenBlock = newBlock("if.then")
            val thenEnd = thenBlock.appendNestedBlock(scope, stmt.thenBlock)

            val elseBlock = newBlock("if.else")
            val elseEnd = if (stmt.elseBlock != null)
                elseBlock.appendNestedBlock(scope, stmt.elseBlock)
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
            val (afterCond, condValue) = condBlock.appendExpression(scope, stmt.condition)

            val bodyBlock = newBlock("while.body")
            val endBlock = BasicBlock("while.end")

            val blocks = LoopBlocks(condBlock, endBlock)

            loopBlockStack.push(blocks)
            val bodyEnd = bodyBlock.appendNestedBlock(scope, stmt.block)
            loopBlockStack.pop()

            currentFunction.append(endBlock)

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
        is ReturnStatement -> {
            val (afterValue, value) = appendExpression(scope, stmt.value)
            afterValue.terminator = Return(value)
            null
        }
    }

    private fun BasicBlock.appendExpression(scope: Scope, exp: Expression): Pair<BasicBlock, Value> = when (exp) {
        is NumberLiteral -> {
            this to Constant(i32, exp.value.toInt())
        }
        is BooleanLiteral -> {
            this to Constant(bool, if (exp.value) 1 else 0)
        }
        is IdentifierExpression -> {
            val variable = scope.find(exp.identifier)
                           ?: throw IdNotFoundException(exp.position, exp.identifier)
            this to variable.loadValue(this)
        }
        is Assignment -> {
            if (exp.target !is IdentifierExpression) TODO("other target types")
            val assignTarget = scope.find(exp.target.identifier)
                               ?: throw IdNotFoundException(exp.target.position, exp.target.identifier)
            val (next, value) = appendExpression(scope, exp.value)
            assignTarget.storeValue(next, value)
            next to value
        }
        is BinaryOp -> {
            val (afterLeft, leftValue) = appendExpression(scope, exp.left)
            val (afterRight, rightValue) = afterLeft.appendExpression(scope, exp.right)
            val result = IrBinaryOp(null, exp.type, leftValue, rightValue)
            afterRight.append(result)
            afterRight to result
        }
        is UnaryOp -> {
            val (afterValue, value) = appendExpression(scope, exp.value)
            val result = IrUnaryOp(null, exp.type, value)
            afterValue.append(result)
            afterValue to result
        }
        is Call -> {
            if (exp.target is IdentifierExpression) {
                when (exp.target.identifier) {
                    "eat" -> {
                        val result = Eat()
                        val after = exp.arguments.fold(this) { before, operand ->
                            val (after, value) = before.appendExpression(scope, operand)
                            result.addOperand(value)
                            after
                        }
                        after.append(result)
                        after to result
                    }
                    "blur" -> {
                        require(exp.arguments.size == 1) { "blur takes a single argument" }
                        val (after, value) = appendExpression(scope, exp.arguments.first())
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

    private fun resolveType(annotation: TypeAnnotation?, default: Type) =
            if (annotation == null) default else resolveType(annotation)

    private fun resolveType(annotation: TypeAnnotation) = when (annotation.str) {
        "bool" -> bool
        "i32" -> i32
        else -> throw IllegalTypeException(annotation.position, annotation.str)
    }
}

class IdNotFoundException(pos: SourcePosition, identifier: String)
    : Exception("$pos: '$identifier' not found")

class DuplicateDeclarationException(pos: SourcePosition, identifier: String)
    : Exception("$pos: '$identifier' was already declared")

class IllegalTypeException(pos: SourcePosition, type: String)
    : Exception("$pos: Illegal type '$type'")

class ParameterValueImmutableException(name: String)
    : Exception("Can't mutate parameter '$name'")

class DuplicateFunctionDeclaration(pos: SourcePosition, name: String)
    : Exception("Function $name at $pos was already declared")