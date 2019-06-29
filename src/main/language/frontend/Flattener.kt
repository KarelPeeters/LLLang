package language.frontend

import language.ir.AggregateValue
import language.ir.Alloc
import language.ir.ArrayType
import language.ir.BasicBlock
import language.ir.Blur
import language.ir.Branch
import language.ir.Constant
import language.ir.Eat
import language.ir.FunctionType
import language.ir.GetSubPointer
import language.ir.IntegerType.Companion.bool
import language.ir.IntegerType.Companion.i32
import language.ir.Jump
import language.ir.Load
import language.ir.PointerType
import language.ir.Return
import language.ir.Store
import language.ir.StructType
import language.ir.Type
import language.ir.UnitType
import language.ir.UnitValue
import language.ir.Value
import language.ir.pointer
import language.ir.unpoint
import language.parsing.SourcePosition
import java.util.*
import language.ir.BinaryOp as IrBinaryOp
import language.ir.Call as IrCall
import language.ir.Function as IrFunction
import language.ir.Program as IrProgram
import language.ir.UnaryOp as IrUnaryOp

interface LValue : RValue {
    val pointer: Value
}

@Suppress("FunctionName")
fun LValue(pointer: Value): LValue {
    check(pointer.type is PointerType)
    return object : LValue {
        override val pointer = pointer
        override fun loadValue(block: BasicBlock): Value {
            val load = Load(null, pointer)
            block.append(load)
            return load
        }
    }
}

interface RValue {
    fun loadValue(block: BasicBlock): Value
}

@Suppress("FunctionName")
fun RValue(value: Value) = object : RValue {
    override fun loadValue(block: BasicBlock) = value
}

private class Scope(val parent: Scope?) {
    private val vars = mutableMapOf<String, RValue>()
    private val immutables = mutableSetOf<Value>()

    fun register(pos: SourcePosition, name: String, variable: RValue) {
        if (name in vars) throw DuplicateDeclarationException(pos, name)
        vars[name] = variable
    }

    fun registerImmutableVal(pointer: Value) {
        immutables += pointer
    }

    fun find(name: String): RValue? = vars[name] ?: parent?.find(name)

    fun isImmutableVal(value: Value): Boolean = (value in immutables) || (parent?.isImmutableVal(value) ?: false)

    fun nest() = Scope(this)
}

private class LoopBlocks(val header: BasicBlock, val end: BasicBlock)

private data class StructInfo(
        val struct: Struct,
        val type: StructType,
        val methods: Map<String, Pair<Function, IrFunction>>
)

class Flattener {
    val program = IrProgram()

    private lateinit var currentFunction: IrFunction
    private val allocs = mutableListOf<Alloc>()
    private val loopBlockStack = ArrayDeque<LoopBlocks>()
    private val structs = mutableMapOf<String, StructInfo>()

    fun newBlock(name: String? = null) = BasicBlock(name).also { currentFunction.add(it) }

    fun flatten(astProgram: Program) {
        val programScope = Scope(null)
        structs.clear()

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

                    programScope.register(topLevel.position, name, RValue(irFunction))
                    functions.add(topLevel to irFunction)

                    program.addFunction(irFunction)
                }
                is Struct -> {
                    val name = topLevel.name
                    val properties = topLevel.properties.map { resolveType(it.type) }
                    val structType = StructType(name, properties)

                    val methods = topLevel.functions.associate { method ->
                        val irFunction = IrFunction(
                                method.name,
                                listOf("this" to structType.pointer) + method.parameters.map { it.name to resolveType(it.type) },
                                resolveType(method.retType, UnitType)
                        )
                        program.addFunction(irFunction)
                        method.name to (method to irFunction)
                    }
                    structs[name] = StructInfo(topLevel, structType, methods)
                }
            }.also {}
        }

        program.entry = functions.find { it.first.name == "main" }?.second ?: error("No main function in program")

        //generate code
        for ((function, irFunction) in structs.values.flatMap { it.methods.values })
            appendFunction(programScope.nest(), function, true, irFunction)
        for ((function, irFunction) in functions)
            appendFunction(programScope.nest(), function, false, irFunction)
    }

    private fun appendFunction(bodyScope: Scope, function: Function, isMethod: Boolean, irFunction: IrFunction) {
        currentFunction = irFunction
        allocs.clear()
        check(loopBlockStack.isEmpty())

        val entry = newBlock("entry")
        irFunction.entry = entry

        for ((i, irParam) in irFunction.parameters.withIndex()) {
            if (isMethod && i == 0) {
                bodyScope.register(function.position, "this", LValue(irParam))
            } else {
                val astIndex = if (isMethod) i - 1 else i
                val param = function.parameters[astIndex]

                val alloc = Alloc(param.name, irParam.type)
                allocs += alloc
                entry.append(Store(alloc, irParam))

                bodyScope.register(param.position, param.name, LValue(alloc))
            }
        }

        when (val body = function.body) {
            is Function.FunctionBody.Block -> {
                val end = entry.appendNestedBlock(bodyScope, body.block)
                if (end != null) {
                    if (irFunction.returnType != UnitType)
                        throw MissingReturnStatement(function)
                    end.terminator = Return(UnitValue)
                }
            }
            is Function.FunctionBody.Expr -> {
                val (end, value) = entry.appendLoadedExpression(bodyScope, body.exp)
                requireTypeMatch(body.position, irFunction.returnType, value.type)
                end.terminator = Return(value)
            }
        }

        entry.addAll(0, allocs)
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
            val (type, next, value) = if (stmt.value == null) {
                if (stmt.type == null) throw MissingTypeDeclarationException(stmt.position)
                Triple(resolveType(stmt.type), this, null)
            } else {
                val (next, value) = appendLoadedExpression(scope, stmt.value)
                val type = resolveType(stmt.type, value.type)
                requireTypeMatch(stmt.position, type, value.type)
                Triple(type, next, value)
            }

            val alloc = Alloc(stmt.identifier, type)
            allocs += alloc
            val variable = LValue(alloc)

            scope.register(stmt.position, stmt.identifier, variable)
            if (!stmt.mutable)
                scope.registerImmutableVal(alloc)

            if (value != null)
                next.append(Store(alloc, value))
            next
        }
        is CodeBlock -> appendNestedBlock(scope, stmt)
        is IfStatement -> {
            val (afterCond, condValue) = this.appendLoadedExpression(scope, stmt.condition)
            requireTypeMatch(stmt.condition.position, bool, condValue.type)

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
            val (afterCond, condValue) = condBlock.appendLoadedExpression(scope, stmt.condition)

            val bodyBlock = newBlock("while.body")
            val endBlock = BasicBlock("while.end")

            val blocks = LoopBlocks(condBlock, endBlock)

            loopBlockStack.push(blocks)
            val bodyEnd = bodyBlock.appendNestedBlock(scope, stmt.block)
            loopBlockStack.pop()

            currentFunction.add(endBlock)

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
            val (afterValue, value) = stmt.value?.let { value ->
                appendLoadedExpression(scope, value)
            } ?: (this to UnitValue)

            requireTypeMatch(stmt.position, function.returnType, value.type)
            afterValue.terminator = Return(value)
            null
        }
    }

    private fun BasicBlock.appendExpression(scope: Scope, exp: Expression): Pair<BasicBlock, RValue> = when (exp) {
        is NumberLiteral -> {
            this to RValue(Constant(i32, exp.value.toInt()))
        }
        is BooleanLiteral -> {
            this to RValue(Constant(bool, if (exp.value) 1 else 0))
        }
        is IdentifierExpression -> {
            val variable = scope.find(exp.identifier)
                           ?: throw IdNotFoundException(exp.position, exp.identifier)
            this to variable
        }
        is ThisExpression -> {
            val variable = scope.find("this") ?: throw NotInObjectScope(exp.position)
            this to variable
        }
        is Assignment -> {
            val (afterTarget, target) = appendPointerExpression(scope, exp.target)
            if (scope.isImmutableVal(target))
                throw VariableImmutableException(exp.position, (exp.target as IdentifierExpression).identifier)

            val (afterValue, value) = afterTarget.appendLoadedExpression(scope, exp.value)
            requireTypeMatch(exp.position, target.type.unpoint!!, value.type)

            afterValue.append(Store(target, value))
            afterTarget to RValue(value)
        }
        is BinaryOp -> {
            val (afterLeft, leftValue) = appendLoadedExpression(scope, exp.left)
            val (afterRight, rightValue) = afterLeft.appendLoadedExpression(scope, exp.right)
            val result = IrBinaryOp(null, exp.type, leftValue, rightValue)
            afterRight.append(result)
            afterRight to RValue(result)
        }
        is UnaryOp -> {
            val (afterValue, value) = appendLoadedExpression(scope, exp.value)
            val result = IrUnaryOp(null, exp.type, value)
            afterValue.append(result)
            afterValue to RValue(result)
        }
        is Call -> appendCallExpression(scope, exp)
        is DotIndex -> {
            val (after, target) = appendPointerExpression(scope, exp.target)

            val index = resolveStructIndex(exp.position, target.type.unpoint!!, exp.index)
            val result = GetSubPointer.Struct(null, target, index)
            after.append(result)

            after to LValue(result)
        }
        is ArrayInitializer -> {
            val (after, values) = appendLoadedExpressionList(scope, exp.values)

            val innerType = values.firstOrNull()?.type
                            ?: TODO("proper initializer type inference -> support empty arrays")
            for (value in values)
                requireTypeMatch(exp.position, innerType, value.type)

            val arrType = ArrayType(innerType, values.size)
            val arrValue = AggregateValue(null, arrType, values)
            after.append(arrValue)

            after to RValue(arrValue)
        }
        is ArrayIndex -> {
            val (afterTarget, target) = appendPointerExpression(scope, exp.target)
            val (afterIndex, index) = afterTarget.appendLoadedExpression(scope, exp.index)

            val result = GetSubPointer.Array(null, target, index)
            afterIndex.append(result)

            afterIndex to LValue(result)
        }
    }

    private fun BasicBlock.appendCallExpression(scope: Scope, exp: Call): Pair<BasicBlock, RValue> {
        if (exp.target is IdentifierExpression) {
            when (exp.target.identifier) {
                "eat" -> {
                    val (after, arguments) = this.appendLoadedExpressionList(scope, exp.arguments)
                    val eat = Eat(arguments)
                    after.append(eat)
                    return after to RValue(eat)
                }
                "blur" -> {
                    if (exp.arguments.size != 1)
                        throw ArgMismatchException(exp.position, 1, exp.arguments.size)
                    val (after, value) = appendLoadedExpression(scope, exp.arguments.first())
                    val result = Blur(null, value)
                    after.append(result)
                    return after to RValue(result)
                }
                in structs -> {
                    val (after, arguments) = this.appendLoadedExpressionList(scope, exp.arguments)
                    val structInfo = structs.getValue(exp.target.identifier)
                    val type = structInfo.type

                    val argTypes = arguments.map { it.type }
                    if (type.properties != argTypes)
                        throw ArgMismatchException(exp.position, type.properties, argTypes)

                    val value = AggregateValue(null, type, arguments)
                    after.append(value)
                    return after to RValue(value)
                }
            }
        }

        if (exp.target is DotIndex) {
            val (afterThis, thisPtr) = appendPointerExpression(scope, exp.target.target)
            val structInfo = structs.values.first { it.type == thisPtr.type.unpoint!! }
            val target = structInfo.methods[exp.target.index]?.second
                         ?: throw IdNotFoundException(exp.target.position, exp.target.index)

            val (afterArgs, arguments) = afterThis.appendLoadedExpressionList(scope, exp.arguments)

            val call = IrCall(null, target, listOf(thisPtr) + arguments)
            afterArgs.append(call)

            return afterArgs to RValue(call)
        }

        val (afterTarget, target) = appendLoadedExpression(scope, exp.target)
        val (next, arguments) = afterTarget.appendLoadedExpressionList(scope, exp.arguments)

        if (target.type !is FunctionType)
            throw IllegalCallTarget(exp.position, target.type)
        val targetTypes = target.type.paramTypes
        val argTypes = arguments.map { it.type }
        if (argTypes != targetTypes)
            throw ArgMismatchException(exp.position, targetTypes, argTypes)

        val call = IrCall(null, target, arguments)
        next.append(call)
        return next to RValue(call)
    }

    private fun BasicBlock.appendLoadedExpression(scope: Scope, exp: Expression): Pair<BasicBlock, Value> {
        val (after, value) = appendExpression(scope, exp)
        val loaded = value.loadValue(after)
        return after to loaded
    }

    private fun BasicBlock.appendPointerExpression(scope: Scope, exp: Expression): Pair<BasicBlock, Value> {
        val (after, value) = appendExpression(scope, exp)
        if (value !is LValue)
            throw ExptectedLValue(exp.position)
        return after to value.pointer
    }

    private fun BasicBlock.appendLoadedExpressionList(scope: Scope, list: List<Expression>): Pair<BasicBlock, List<Value>> {
        var current = this
        val result = mutableListOf<Value>()

        for (exp in list) {
            val (next, value) = current.appendLoadedExpression(scope, exp)
            current = next
            result += value
        }

        return current to result
    }

    private fun resolveStructIndex(pos: SourcePosition, type: Type, index: String): Int {
        if (type !is StructType)
            throw IllegalDotIndexTarget(pos, type)

        val i = structs.getValue(type.name).struct.properties.indexOfFirst { it.name == index }
        if (i == -1)
            throw IdNotFoundException(pos, index)

        return i
    }

    private fun resolveType(annotation: TypeAnnotation?, default: Type) =
            if (annotation == null) default else resolveType(annotation)

    private fun resolveType(annotation: TypeAnnotation): Type = when (annotation) {
        is TypeAnnotation.Simple -> {
            when (val str = annotation.str) {
                "bool" -> bool
                "i32" -> i32
                in structs -> structs.getValue(str).type

                else -> throw IllegalTypeException(annotation)
            }
        }
        is TypeAnnotation.Function -> {
            FunctionType(
                    annotation.paramTypes.map { resolveType(it) },
                    resolveType(annotation.returnType)
            )
        }
        is TypeAnnotation.Array -> {
            ArrayType(
                    resolveType(annotation.innerType),
                    annotation.size
            )
        }
    }
}

fun requireTypeMatch(pos: SourcePosition, expected: Type, actual: Type) {
    if (expected != actual) throw TypeMismatchException(pos, expected, actual)
}

class IdNotFoundException(pos: SourcePosition, identifier: String)
    : Exception("$pos: '$identifier' not found")

class NotInObjectScope(pos: SourcePosition)
    : Exception("$pos: not in an object scope, 'this' not defined")

class DuplicateDeclarationException(pos: SourcePosition, identifier: String)
    : Exception("$pos: '$identifier' was already declared in this scope")

class MissingTypeDeclarationException(pos: SourcePosition)
    : Exception("Missing type delcaration at $pos")

class IllegalTypeException(type: TypeAnnotation)
    : Exception("Illegal type '$type' at ${type.position}")

class VariableImmutableException(pos: SourcePosition, name: String)
    : Exception("Can't mutate variable '$name' at $pos")

class ExptectedLValue(pos: SourcePosition)
    : Exception("Expected LValue at $pos")

class MissingReturnStatement(function: Function)
    : Exception("Function ${function.name} at ${function.position} missing return statement")

class TypeMismatchException(pos: SourcePosition, expected: Type, actual: Type)
    : Exception("Expected type was $expected, actual $actual at $pos")

class ArgMismatchException : Exception {
    constructor(pos: SourcePosition, expected: List<Type>, actual: List<Type>) :
            super("Argument mismatch: expected $expected, got $actual at $pos")

    constructor(pos: SourcePosition, expectedCount: Int, actualCount: Int) :
            super("Argument mismatch: expected $expectedCount arguments, got $actualCount at $pos")
}

class IllegalCallTarget(pos: SourcePosition, type: Type)
    : Exception("Can't call type $type at $pos")

class IllegalDotIndexTarget(pos: SourcePosition, type: Type)
    : Exception("Can't dot index type $type at $pos")
