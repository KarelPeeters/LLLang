package language.frontend

import language.ir.*
import language.ir.IntegerType.Companion.bool
import language.ir.IntegerType.Companion.i32
import language.ir.support.UserInfo
import language.parsing.SourcePosition
import java.util.*
import language.ir.BinaryOp as IrBinaryOp
import language.ir.Call as IrCall
import language.ir.Function as IrFunction
import language.ir.Parameter as IrParameter
import language.ir.Program as IrProgram

data class ValueCont(val value: Node, val afterMem: Node)

interface RValue {
    fun loadValue(beforeMem: Node): ValueCont
}

interface LValue : RValue {
    val pointer: Node
}

@Suppress("FunctionName")
fun RValue(value: Node) = object : RValue {
    override fun loadValue(beforeMem: Node) = ValueCont(value, beforeMem)
}


object VoidRValue : RValue {
    override fun loadValue(beforeMem: Node) = throw IllegalLoadVoidValueException()
}

@Suppress("FunctionName")
fun LValue(pointer: Node): LValue {
    check(pointer.type is PointerType)
    return object : LValue {
        override val pointer = pointer
        override fun loadValue(beforeMem: Node): ValueCont {
            val load = Load(beforeMem, this.pointer)
            load.result.name = this.pointer.name
            return ValueCont(load.result, load.afterMem)
        }
    }
}

private class Scope(val parent: Scope?) {
    private val vars = mutableMapOf<String, RValue>()
    private val immutables = mutableSetOf<Node>()

    fun register(pos: SourcePosition, name: String, variable: RValue) {
        if (name in vars) throw DuplicateDeclarationException(pos, name)
        vars[name] = variable
    }

    fun registerImmutableVal(pointer: Node) {
        immutables += pointer
    }

    fun find(name: String): RValue? = vars[name] ?: parent?.find(name)

    fun isImmutableVal(value: Node): Boolean = (value in immutables) || (parent?.isImmutableVal(value) ?: false)

    fun nest() = Scope(this)
}

private class LoopInfo(
        val headerRegion: Region,
        val headerMemPhi: Phi,
        val endRegion: Region,
        val endMemPhi: Phi
)

private class Cont(val region: Region, val mem: Node)

private data class StructInfo(
        val struct: Struct,
        val type: StructType,
        val methods: Map<String, Pair<Function, IrFunction>>
)

class Flattener private constructor() {
    companion object {
        fun flatten(program: Program): IrProgram = Flattener().flatten(program)
    }

    private val structs = mutableMapOf<String, StructInfo>()
    private val startMemBlurs = mutableListOf<Blur>()

    private lateinit var currentFunction: IrFunction
    private val initialAllocs = mutableListOf<Pair<Alloc, IrParameter?>>()
    private val loopStack = ArrayDeque<LoopInfo>()

    fun flatten(astProgram: Program): language.ir.Program {
        val irProgram = IrProgram()
        val programScope = Scope(null)
        structs.clear()

        val functions = mutableListOf<Pair<Function, IrFunction>>()

        //register top levels
        for (topLevel in astProgram.toplevels) {
            when (topLevel) {
                is Function -> {
                    val funcName = topLevel.name
                    val type = FunctionType(
                            listOf(MemType) + topLevel.parameters.mapNotNull { resolveType(it.type) },
                            listOfNotNull(MemType, resolveType(topLevel.retType, null))
                    )
                    val irFunction = IrFunction(type)

                    irFunction.name = funcName
                    for ((i, param) in topLevel.parameters.withIndex())
                        irFunction.parameters[i + 1].name = param.name

                    programScope.register(topLevel.position, funcName, RValue(irFunction))
                    functions.add(topLevel to irFunction)
                    Unit
                }
                is Struct -> {
                    val name = topLevel.name
                    val properties = topLevel.properties.map { resolveNonVoidType(it.type) }
                    val structType = StructType(name, properties)

                    val methods = topLevel.functions.associate { method ->
                        val funcType = FunctionType(
                                listOf(MemType, structType.pointer) + method.parameters.map { resolveNonVoidType(it.type) },
                                listOfNotNull(MemType, resolveType(method.retType, VoidType))
                        )
                        val irFunction = IrFunction(funcType)

                        irFunction.name = method.name
                        irFunction.parameters[1].name = "this"
                        for ((i, param) in method.parameters.withIndex())
                            irFunction.parameters[i + 1].name = param.name
                        method.name to (method to irFunction)
                    }
                    structs[name] = StructInfo(topLevel, structType, methods)
                    Unit
                }
            }.hashCode() //exhaustive
        }

        val (mainFunc, mainIrFunc) = functions.singleOrNull {
            it.first.name == "main"
        } ?: error("There must be a single main function")

        val mainTypeWithoutMem = FunctionType(
                mainIrFunc.type.parameters.drop(1),
                mainIrFunc.type.returns.drop(1)
        )
        requireTypeMatch(mainFunc.position, FunctionType(emptyList(), emptyList()), mainTypeWithoutMem)
        irProgram.entry = mainIrFunc

        //generate code
        for ((function, irFunction) in structs.values.flatMap { it.methods.values })
            appendFunction(programScope.nest(), function, true, irFunction)
        for ((function, irFunction) in functions)
            appendFunction(programScope.nest(), function, false, irFunction)

        //replace startMem transparent blurs
        val userInfo = UserInfo(irProgram)
        for (blur in startMemBlurs)
            userInfo.replaceNode(blur, blur.value)

        return irProgram
    }

    private fun appendFunction(bodyScope: Scope, function: Function, isMethod: Boolean, irFunction: IrFunction) {
        check(loopStack.isEmpty())
        currentFunction = irFunction

        val entry = Region("entry")
        irFunction.entry = entry

        //register & alloc parameters
        for ((i, irParam) in irFunction.parameters.withIndex()) {
            if (i == 0) continue //skip mem parameter

            if (isMethod && i == 1) {
                bodyScope.register(function.position, "this", LValue(irParam))
            } else {
                val astIndex = if (isMethod) i - 2 else i - 1
                val param = function.parameters[astIndex]

                val alloc = Alloc(param.name, PlaceHolder(MemType), irParam.type)
                initialAllocs += alloc to irParam
                bodyScope.register(param.position, param.name, LValue(alloc.result))
            }
        }

        val startMem = Blur(PlaceHolder(MemType), transparent = true)
        startMemBlurs += startMem
        val start = Cont(entry, startMem)

        //append body code
        when (val body = function.body) {
            is Function.FunctionBody.Block -> {
                val end = appendNestedBlock(start, bodyScope, body.block)
                if (end != null) {
                    if (irFunction.type.returns.size != 1)
                        throw MissingReturnStatement(function)
                    end.region.terminator = Return(listOf(end.mem))
                }
            }
            is Function.FunctionBody.Expr -> {
                val (end, value) = appendLoadedExpression(start, bodyScope, body.exp) ?: return
                requireTypeMatch(body.position, irFunction.type.returns[1], value.type)
                end.region.terminator = Return(listOf(end.mem, value))
            }
        }

        //string all allocs together (including later declared variables)
        val initialMem: Node = irFunction.parameters[0]
        val afterAllocMem = initialAllocs.fold(initialMem) { mem, (alloc, _) ->
            alloc.beforeMem = mem
            alloc.afterMem
        }
        //store parameters into their allocs
        val afterParamStoreMem = initialAllocs.fold(afterAllocMem) { mem, (alloc, param) ->
            if (param != null) Store(mem, alloc.result, param) else mem
        }

        startMem.value = afterParamStoreMem
        initialAllocs.clear()
    }

    private fun appendNestedBlock(start: Cont, scope: Scope, block: CodeBlock): Cont? {
        val nested = scope.nest()

        return block.statements.fold(start) { cont: Cont, statement ->
            appendStatement(cont, nested, statement) ?: return null
        }
    }

    private fun appendStatement(start: Cont, scope: Scope, stmt: Statement): Cont? {
        return when (stmt) {
            is Expression -> appendExpression(start, scope, stmt)?.first
            is Declaration -> {
                //load the value and figure out the type
                val (type, cont, value) = if (stmt.value == null) {
                    if (stmt.type == null)
                        throw MissingTypeDeclarationException(stmt.position)
                    Triple(resolveNonVoidType(stmt.type), start, null)
                } else {
                    val (next, value) = appendLoadedExpression(start, scope, stmt.value) ?: return null
                    val type = resolveNonVoidType(stmt.type, value.type)
                    requireTypeMatch(stmt.position, type, value.type)
                    Triple(type, next, value)
                }

                //allocate
                val alloc = Alloc(stmt.identifier, PlaceHolder(MemType), type)
                initialAllocs += alloc to null
                val variable = LValue(alloc.result)

                scope.register(stmt.position, stmt.identifier, variable)
                if (!stmt.mutable)
                    scope.registerImmutableVal(alloc)

                //store the value
                if (value != null) {
                    val store = Store(cont.mem, alloc.result, value)
                    Cont(cont.region, store)
                } else
                    cont
            }
            is CodeBlock -> appendNestedBlock(start, scope, stmt)
            is IfStatement -> {
                val (afterCond, condValue) = appendLoadedExpression(start, scope, stmt.condition) ?: return null
                requireTypeMatch(stmt.condition.position, bool, condValue.type)

                val thenStart = Cont(Region("if.then"), afterCond.mem)
                val thenEnd = appendNestedBlock(thenStart, scope, stmt.thenBlock)

                val elseStart = Cont(Region("if.else"), afterCond.mem)
                val elseEnd = if (stmt.elseBlock != null)
                    appendNestedBlock(elseStart, scope, stmt.elseBlock)
                else
                    elseStart

                afterCond.region.terminator = Branch(condValue, thenStart.region, elseStart.region)

                if (thenEnd != null || elseEnd != null) {
                    val end = Region("if.end")
                    val endMem = Phi(MemType, end)

                    if (thenEnd != null) {
                        thenEnd.region.terminator = Jump(end)
                        endMem.values[thenEnd.region] = thenEnd.mem
                    }
                    if (elseEnd != null) {
                        elseEnd.region.terminator = Jump(end)
                        endMem.values[elseEnd.region] = elseEnd.mem
                    }

                    Cont(end, endMem)
                } else null
            }
            is WhileStatement -> {
                val condRegion = Region("while.cond")
                val condPhi = Phi(MemType, condRegion)
                val condStart = Cont(condRegion, condPhi)
                val (condEnd, condValue) = appendLoadedExpression(condStart, scope, stmt.condition) ?: return null

                val endRegion = Region("while.end")
                val endPhi = Phi(MemType, endRegion)

                val blocks = LoopInfo(condRegion, condPhi, endRegion, endPhi)
                loopStack.push(blocks)
                val bodyStart = Cont(Region("while.body"), condEnd.mem)
                val bodyEnd = appendNestedBlock(bodyStart, scope, stmt.block)
                loopStack.pop()

                start.region.terminator = Jump(condRegion)
                condEnd.region.terminator = Branch(condValue, bodyStart.region, endRegion)

                if (bodyEnd != null) {
                    bodyEnd.region.terminator = Jump(condRegion)
                    condPhi.values[bodyEnd.region] = bodyEnd.mem
                }

                condPhi.values[start.region] = start.mem
                endPhi.values[condRegion] = condPhi

                //potentially endless loop, keep memory node alive
                currentFunction.keepAlive += endPhi

                Cont(endRegion, endPhi)
            }
            is BreakStatement -> {
                val info = loopStack.peek()

                start.region.terminator = Jump(info.endRegion)
                info.endMemPhi.values[start.region] = start.mem

                null
            }
            is ContinueStatement -> {
                val info = loopStack.peek()

                start.region.terminator = Jump(info.headerRegion)
                info.headerMemPhi.values[start.region] = start.mem

                null
            }
            is ReturnStatement -> {
                val (valueEnd, value) = if (stmt.value != null) {
                    appendLoadedExpression(start, scope, stmt.value) ?: null to null
                } else {
                    start to null
                }

                if (valueEnd != null) {
                    val expected = currentFunction.type.returns.getOrNull(1) ?: VoidType
                    val actual = value?.type ?: VoidType
                    requireTypeMatch(stmt.position, expected, actual)

                    val values = if (value != null) listOf(valueEnd.mem, value) else listOf(valueEnd.mem)
                    valueEnd.region.terminator = Return(values)
                }
                null
            }
        }
    }

    private fun appendExpression(start: Cont, scope: Scope, exp: Expression): Pair<Cont, RValue>? {
        return when (exp) {
            is NumberLiteral -> {
                start to RValue(IntegerConstant(i32, exp.value.toInt()))
            }
            is BooleanLiteral -> {
                start to RValue(IntegerConstant(bool, if (exp.value) 1 else 0))
            }
            is IdentifierExpression -> {
                val variable = scope.find(exp.identifier)
                               ?: throw IdNotFoundException(exp.position, exp.identifier)
                start to variable
            }
            is ThisExpression -> {
                val variable = scope.find("this") ?: throw NotInObjectScope(exp.position)
                start to variable
            }
            is Assignment -> {
                val (targetEnd, target) = appendPointerExpression(start, scope, exp.target) ?: return null
                if (scope.isImmutableVal(target))
                    throw VariableImmutableException(exp.position, (exp.target as IdentifierExpression).identifier)

                val (valueEnd, value) = appendLoadedExpression(targetEnd, scope, exp.value) ?: return null
                requireTypeMatch(exp.position, target.type.unpoint!!, value.type)

                val store = Store(valueEnd.mem, target, value)
                Cont(valueEnd.region, store) to RValue(value)
            }
            is BinaryOp -> {
                val (leftEnd, leftValue) = appendLoadedExpression(start, scope, exp.left) ?: return null
                val (rightEnd, rightValue) = appendLoadedExpression(leftEnd, scope, exp.right) ?: return null
                val result = IrBinaryOp(exp.type, leftValue, rightValue)
                rightEnd to RValue(result)
            }
            is UnaryOp -> {
                when (exp.type) {
                    UnaryOpType.Not -> appendBasicUnaryExpression(start, scope, exp, -1, ArithmeticOpType.Xor)
                    UnaryOpType.Plus -> appendBasicUnaryExpression(start, scope, exp, 0, ArithmeticOpType.Add)
                    UnaryOpType.Minus -> appendBasicUnaryExpression(start, scope, exp, 0, ArithmeticOpType.Sub)

                    UnaryOpType.PreInc -> appendIncDecExpression(start, scope, exp, ArithmeticOpType.Add, pre = true)
                    UnaryOpType.PostInc -> appendIncDecExpression(start, scope, exp, ArithmeticOpType.Add, pre = false)
                    UnaryOpType.PreDec -> appendIncDecExpression(start, scope, exp, ArithmeticOpType.Sub, pre = true)
                    UnaryOpType.PostDec -> appendIncDecExpression(start, scope, exp, ArithmeticOpType.Sub, pre = false)
                }
            }
            is Call -> appendCallExpression(start, scope, exp)
            is DotIndex -> TODO()
            is ArrayIndex -> TODO()
            is ArrayInitializer -> TODO()
        }
    }

    private fun appendBasicUnaryExpression(start: Cont, scope: Scope, exp: UnaryOp, left: Int, type: BinaryOpType): Pair<Cont, RValue>? {
        val (afterValue, value) = appendLoadedExpression(start, scope, exp.value) ?: return null
        requireIntegerType(exp.value.position, value.type)
        val result = IrBinaryOp(type, IntegerConstant(value.type as IntegerType, left), value)
        return afterValue to RValue(result)
    }

    private fun appendIncDecExpression(start: Cont, scope: Scope, exp: UnaryOp, binaryOpType: BinaryOpType, pre: Boolean): Pair<Cont, RValue>? {
        val (targetEnd, target) = appendPointerExpression(start, scope, exp.value) ?: return null
        if (scope.isImmutableVal(target))
            throw VariableImmutableException(exp.position, (exp.value as IdentifierExpression).identifier)

        val old = Load(targetEnd.mem, target)
        val type = requireIntegerType(exp.value.position, old.type)
        val new = IrBinaryOp(binaryOpType, old, type.ONE)

        val store = Store(targetEnd.mem, target, new)
        val cont = Cont(targetEnd.region, store)
        val result: Node = if (pre) new else old

        return cont to RValue(result)
    }

    private fun appendCallExpression(start: Cont, scope: Scope, exp: Call): Pair<Cont, RValue>? {
        if (exp.target is IdentifierExpression) {
            //special case "functions"
            when (exp.target.identifier) {
                "eat" -> {
                    val (argsEnd, args) = appendLoadedExpressionList(start, scope, exp.arguments) ?: return null
                    val mem = args.fold(argsEnd.mem) { mem, arg -> Eat(mem, arg) }

                    val cont = Cont(argsEnd.region, mem)
                    return cont to VoidRValue
                }
                "blur" -> {
                    if (exp.arguments.size != 1)
                        throw ArgMismatchException(exp.position, 1, exp.arguments.size)
                    val (valueEnd, value) = appendLoadedExpression(start, scope, exp.arguments.first()) ?: return null

                    val result = Blur(value, transparent = false)
                    return valueEnd to RValue(result)
                }
                in structs -> TODO()
            }
        }

        val (callRegion, call) = if (exp.target is DotIndex) {
            //struct function
            val (afterThis, thisPtr) = appendPointerExpression(start, scope, exp.target.target) ?: return null
            val structInfo = structs.values.first { it.type == thisPtr.type.unpoint!! }
            val target = structInfo.methods[exp.target.index]?.second
                         ?: throw IdNotFoundException(exp.target.position, exp.target.index)

            val (afterArgs, arguments) = appendLoadedExpressionList(afterThis, scope, exp.arguments) ?: return null

            afterArgs.region to IrCall(target, listOf(thisPtr, afterArgs.mem) + arguments)
        } else {
            //standard function
            val (afterTarget, target) = appendLoadedExpression(start, scope, exp.target) ?: return null
            val (afterArgs, args) = appendLoadedExpressionList(afterTarget, scope, exp.arguments) ?: return null

            val targetType = target.type
            if (targetType !is FunctionType)
                throw IllegalCallTargetException(exp.position, targetType)

            val argTypes = args.map { it.type }
            if (argTypes != targetType.parameters.drop(1))
                throw ArgMismatchException(exp.position, targetType.parameters.drop(1), argTypes)

            afterArgs.region to IrCall(target, listOf(afterArgs.mem) + args)
        }

        val afterCall = Cont(callRegion, call.returns[0])
        val rValue = if (call.returns.size == 2) RValue(call.returns[1]) else VoidRValue
        return afterCall to rValue
    }

    private fun appendLoadedExpression(start: Cont, scope: Scope, exp: Expression): Pair<Cont, Node>? {
        val (valueEnd, target) = appendExpression(start, scope, exp) ?: return null
        val (value, loadMem) = target.loadValue(valueEnd.mem)
        return Cont(valueEnd.region, loadMem) to value
    }

    private fun appendPointerExpression(start: Cont, scope: Scope, exp: Expression): Pair<Cont, Node>? {
        val (after, value) = appendExpression(start, scope, exp) ?: return null
        if (value !is LValue)
            throw ExptectedLValue(exp.position)
        return after to value.pointer
    }

    private fun appendLoadedExpressionList(start: Cont, scope: Scope, list: List<Expression>): Pair<Cont, List<Node>>? {
        val result = mutableListOf<Node>()

        val valuesEnd = list.fold(start) { cont, exp ->
            val (end, value) = appendLoadedExpression(cont, scope, exp) ?: return null
            result += value
            end
        }

        return valuesEnd to result
    }

    private fun resolveNonVoidType(annotation: TypeAnnotation?, default: Type): Type =
            if (annotation == null) default else resolveNonVoidType(annotation)

    private fun resolveNonVoidType(annotation: TypeAnnotation): Type =
            resolveType(annotation) ?: throw UnexpectedVoidType()

    private fun resolveType(annotation: TypeAnnotation?, default: Type?): Type? =
            if (annotation == null) default else resolveType(annotation)

    private fun resolveType(annotation: TypeAnnotation): Type? = when (annotation) {
        is TypeAnnotation.Simple -> {
            when (val str = annotation.str) {
                "bool" -> bool
                "i32" -> i32
                "Unit" -> null
                in structs -> structs.getValue(str).type

                else -> throw IllegalTypeException(annotation)
            }
        }
        is TypeAnnotation.Function -> {
            FunctionType(
                    listOf(MemType) + annotation.paramTypes.map { resolveNonVoidType(it) },
                    listOfNotNull(MemType, resolveType(annotation.returnType))
            )
        }
        is TypeAnnotation.Array -> TODO()
    }
}

@Suppress("FunctionName")
private fun Region(name: String) = Region().apply {
    this.name = name
}

@Suppress("FunctionName")
private fun Alloc(name: String, beforeMem: Node, inner: Type) = Alloc(beforeMem, inner).apply {
    this.result.name = name
}

private fun requireTypeMatch(pos: SourcePosition, expected: Type, actual: Type) {
    if (expected != actual) throw TypeMismatchException(pos, expected, actual)
}

private fun requireIntegerType(pos: SourcePosition, actual: Type): IntegerType =
        actual as? IntegerType ?: throw ExpectedIntegerTypeException(pos, actual)

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
    : Exception("Function '${function.name}' at ${function.position} missing return statement")

class TypeMismatchException(pos: SourcePosition, expected: Type, actual: Type)
    : Exception("Expected type was '$expected', actual '$actual' at $pos")

class ExpectedIntegerTypeException(pos: SourcePosition, actual: Type)
    : Exception("Expected an integer type, got '$actual' at $pos")

class ArgMismatchException : Exception {
    constructor(pos: SourcePosition, expected: List<Type>, actual: List<Type>) :
            super("Argument mismatch: expected '$expected', got '$actual' at $pos")

    constructor(pos: SourcePosition, expectedCount: Int, actualCount: Int) :
            super("Argument mismatch: expected $expectedCount arguments, got $actualCount at $pos")
}

class IllegalCallTargetException(pos: SourcePosition, type: Type)
    : Exception("Can't call type '$type' at $pos")

class IllegalDotIndexTargetException(pos: SourcePosition, type: Type)
    : Exception("Can't dot index type '$type' at $pos")

class IllegalLoadVoidValueException
    : Exception("Attempting to load VoidValue")

class UnexpectedVoidType
    : Exception("Unexpected VoidType")