package language.frontend

import language.ir.Alloc
import language.ir.BasicBlock
import language.ir.Blur
import language.ir.Branch
import language.ir.Constant
import language.ir.Eat
import language.ir.FunctionType
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
import language.ir.unpoint
import language.util.mapFold
import java.util.*
import language.ir.BinaryOp as IrBinaryOp
import language.ir.Call as IrCall
import language.ir.Function as IrFunction
import language.ir.Program as IrProgram
import language.ir.UnaryOp as IrUnaryOp

sealed class Variable(val name: String, val type: Type, val mutable: Boolean) {
    abstract fun loadValue(block: BasicBlock): Value
    abstract fun storeValue(block: BasicBlock, value: Value)

    class Memory(name: String, val pointer: Value, mutable: Boolean)
        : Variable(name, pointer.type.unpoint!!, mutable) {
        override fun loadValue(block: BasicBlock) = Load(name, pointer).also { block.append(it) }
        override fun storeValue(block: BasicBlock, value: Value) {
            block.append(Store(pointer, value))
        }
    }

    class FixedValue(name: String, val value: Value)
        : Variable(name, value.type, false) {
        override fun loadValue(block: BasicBlock) = value
        override fun storeValue(block: BasicBlock, value: Value) = throw IllegalStateException()
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

    private lateinit var currentFunction: IrFunction
    private val allocs = mutableListOf<Alloc>()
    private val loopBlockStack = ArrayDeque<LoopBlocks>()

    fun newBlock(name: String? = null) = BasicBlock(name).also { currentFunction.append(it) }

    fun flatten(astProgram: Program) {
        val programScope = Scope(null)
        val functions = mutableListOf<Pair<Function, IrFunction>>()

        //register top levels
        for (topLevel in astProgram.toplevels) {
            when (topLevel) {
                is Function -> {
                    val name = topLevel.name
                    val irFunction = IrFunction(
                            name,
                            topLevel.parameters.map { it.name to resolveType(it.type) },
                            resolveType(topLevel.retType, UnitType)
                    )

                    programScope.register(topLevel.position, Variable.FixedValue(name, irFunction))
                    functions.add(topLevel to irFunction)

                    program.addFunction(irFunction)
                    if (name == "main")
                        program.entry = irFunction
                }
            }
        }

        //generate code
        for ((function, irFunction) in functions) {
            appendFunction(programScope, function, irFunction)
        }
    }

    private fun appendFunction(scope: Scope, function: Function, irFunction: IrFunction) {
        currentFunction = irFunction
        allocs.clear()
        require(loopBlockStack.isEmpty())

        val bodyScope = Scope(scope)
        for ((param, value) in function.parameters.zip(irFunction.parameters)) {
            bodyScope.register(param.position, Variable.FixedValue(param.name, value))
        }

        val entry = newBlock("entry")
        irFunction.entry = entry

        val body = function.body
        when (body) {
            is Function.FunctionBody.Block -> {
                val end = entry.appendNestedBlock(bodyScope, body.block)
                if (end != null) {
                    if (irFunction.returnType != UnitType)
                        throw MissingReturnStatement(function)
                    end.terminator = Return(UnitValue)
                }
            }
            is Function.FunctionBody.Expr -> {
                val (end, value) = entry.appendExpression(bodyScope, body.exp)
                requireTypeMatch(body.exp.position, irFunction.returnType, value.type)
                end.terminator = Return(value)
            }
        }

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

            val variable = Variable.Memory(stmt.identifier, alloc, stmt.mutable)
            scope.register(stmt.position, variable)

            val (next, value) = appendExpression(scope, stmt.value)
            requireTypeMatch(stmt.position, type, value.type)
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


            afterCond.terminator = Branch(condValue, thenBlock, elseBlock)
            if (thenEnd != null || elseEnd != null) {
                val end = newBlock("if.end")
                thenEnd?.terminator = Jump(end)
                elseEnd?.terminator = Jump(end)
                end
            } else null

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
            requireTypeMatch(stmt.position, function.returnType, value.type)
            afterValue.terminator = Return(value)
            null
        }
    }

    private fun BasicBlock.appendExpression(scope: Scope, exp: Expression?): Pair<BasicBlock, Value> = when (exp) {
        null -> {
            this to UnitValue
        }
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
            requireTypeMatch(exp.position, assignTarget.type, value.type)

            if (!assignTarget.mutable)
                throw VariableImmutableException(exp.position, exp.target.identifier)
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
            val ret = if (exp.target is IdentifierExpression) {
                when (exp.target.identifier) {
                    "eat" -> {
                        val eat = Eat()
                        val (after, operands) = exp.arguments.mapFold(this) { block, operand ->
                            block.appendExpression(scope, operand)
                        }
                        eat.addOperands(operands)
                        after.append(eat)
                        after to eat
                    }
                    "blur" -> {
                        require(exp.arguments.size == 1) { "blur takes a single argument" }
                        val (after, value) = appendExpression(scope, exp.arguments.first())
                        val result = Blur(value)
                        after.append(result)
                        after to result
                    }
                    else -> null
                }
            } else null

            ret ?: run {
                val (afterTarget, target) = appendExpression(scope, exp.target)
                val (next, arguments) = exp.arguments.mapFold(afterTarget) { block, arg ->
                    block.appendExpression(scope, arg)
                }

                val targetTypes = (target.type as FunctionType).paramTypes
                val argTypes = arguments.map { it.type }
                if (argTypes != targetTypes)
                    throw ArgMismatchException(exp.position, targetTypes, argTypes)

                val call = IrCall(null, target, arguments)
                next.append(call)
                next to call
            }
        }
        is Index -> TODO("index")
    }

    private fun resolveType(annotation: TypeAnnotation?, default: Type) =
            if (annotation == null) default else resolveType(annotation)

    private fun resolveType(annotation: TypeAnnotation): Type = when (annotation) {
        is TypeAnnotation.Simple -> when (annotation.str) {
            "bool" -> bool
            "i32" -> i32
            else -> throw IllegalTypeException(annotation)
        }
        is TypeAnnotation.Function -> {
            FunctionType(
                    annotation.paramTypes.map { resolveType(it) },
                    resolveType(annotation.returnType)
            )
        }
    }
}

fun requireTypeMatch(pos: SourcePosition, expected: Type, actual: Type) {
    if (expected != actual) throw TypeMismatchException(pos, expected, actual)
}

class IdNotFoundException(pos: SourcePosition, identifier: String)
    : Exception("$pos: '$identifier' not found")

class DuplicateDeclarationException(pos: SourcePosition, identifier: String)
    : Exception("$pos: '$identifier' was already declared")

class IllegalTypeException(type: TypeAnnotation)
    : Exception("${type.position}: Illegal type '$type'")

class VariableImmutableException(pos: SourcePosition, name: String)
    : Exception("Can't mutate variable '$name' at $pos")

class MissingReturnStatement(function: Function)
    : Exception("Function ${function.name} at ${function.position} missing return statement")

class TypeMismatchException(pos: SourcePosition, expected: Type, actual: Type)
    : Exception("Expected type was $expected, actual $actual at $pos")

class ArgMismatchException(pos: SourcePosition, expected: List<Type>, actual: List<Type>)
    : Exception("Argument mismatch: expected $expected, got $actual at $pos")
