package language.interpreter

import language.ir.Alloc
import language.ir.BasicBlock
import language.ir.BinaryOp
import language.ir.Blur
import language.ir.Branch
import language.ir.Call
import language.ir.Constant
import language.ir.Eat
import language.ir.Exit
import language.ir.Function
import language.ir.Instruction
import language.ir.IntegerType
import language.ir.IntegerType.Companion.bool
import language.ir.Jump
import language.ir.Load
import language.ir.Phi
import language.ir.Program
import language.ir.Return
import language.ir.Store
import language.ir.Terminator
import language.ir.Type
import language.ir.UnaryOp
import language.ir.UnitType
import language.ir.UnitValue
import language.ir.Value
import language.ir.unpoint

sealed class ValueInst(val type: Type) {
    abstract fun shortString(): String
}

object UnitInst : ValueInst(UnitType) {
    override fun shortString() = "unit"
}

class IntegerInst(type: Type, val value: Int) : ValueInst(type) {
    init {
        require(type is IntegerType)
    }

    override fun shortString() = "$value"
    override fun toString() = "$type $value"
}

class BoxInst(type: Type, value: ValueInst?) : ValueInst(type) {
    var value: ValueInst? = value
        set(value) {
            require(type.unpoint!! == value!!.type); field = value
        }

    init {
        value?.let { require(type.unpoint!! == it.type) }
    }

    override fun shortString() = "$value]"
    override fun toString() = "$type [$value]"
}

typealias ValuesMap = Map<Value, ValueInst>

class StackFrame(
        val values: ValuesMap,
        val current: Instruction,
        val prevBlock: BasicBlock?
) {
    val currBlock = current.block
    val currFunction = current.block.function
}

class State(val stack: List<StackFrame>) {
    val topFrame = stack.last()
}

class Interpreter(val program: Program) {
    var steps: Int = 0
        private set

    private suspend fun SequenceScope<State>.callFunction(
            function: Function,
            args: List<ValueInst>,
            stack: List<StackFrame>
    ): ValueInst? {
        val values: MutableMap<Value, ValueInst> = mutableMapOf()

        //parameters
        require(args.size == function.parameters.size) { "args size must match param size" }
        for ((arg, param) in args.zip(function.parameters)) {
            require(arg.type == param.type) { "arg and param types must match" }
            values[param] = arg
        }

        //run body
        var prevBlock: BasicBlock? = null
        var currBlock = function.entry

        loop@ while (true) {
            require(currBlock in function.blocks) { "all visited blocks must be listed in function" }

            for ((i, instr) in currBlock.instructions.withIndex()) {
                val frame = StackFrame(values, instr, prevBlock)
                yield(State(stack + frame))

                if (i == currBlock.instructions.lastIndex)
                    require(instr is Terminator) { "BasicBlocks must end with a terminator" }

                val result: ValueInst? = when (instr) {
                    is Alloc -> {
                        require(instr !in values) { "alloc instructions can only callFunction once" }
                        BoxInst(instr.type, null)
                    }
                    is Store -> {
                        val box = values.getInst(instr.pointer) as BoxInst
                        box.value = values.getInst(instr.value)
                        null
                    }
                    is Load -> {
                        (values.getInst(instr.pointer) as BoxInst).value!!
                    }
                    is BinaryOp -> {
                        val leftInst = values.getInst(instr.left) as IntegerInst
                        val rightInst = values.getInst(instr.right) as IntegerInst
                        val left = Constant(leftInst.type, leftInst.value)
                        val right = Constant(rightInst.type, rightInst.value)
                        val result = instr.opType.calculate(left, right)
                        IntegerInst(result.type, result.value)
                    }
                    is UnaryOp -> {
                        val valueInst = values.getInst(instr.value) as IntegerInst
                        val value = Constant(valueInst.type, valueInst.value)
                        val result = instr.opType.calculate(value)
                        IntegerInst(result.type, result.value)
                    }
                    is Phi -> {
                        values.getInst(instr.sources.getValue(prevBlock!!))
                    }
                    is Eat -> {
                        for (operand in instr.operands)
                            values.getInst(operand)
                        null
                    }
                    is Blur -> {
                        values.getInst(instr.value)
                    }
                    is Call -> {
                        TODO("call")
                        /*val callArgs = instr.arguments.map { values.getInst(it) }
                        callFunction(instr.function, callArgs, stack + frame)
                        ?: return null //propagate exit*/
                    }
                    is Terminator -> {
                        require(i == currBlock.instructions.lastIndex) { "Terminators can only appear at the end of a BasicBlock" }
                        prevBlock = currBlock

                        val nextBlock = when (instr) {
                            is Branch -> {
                                val inst = values.getInst(instr.value, bool) as IntegerInst
                                when (inst.value) {
                                    0 -> instr.ifFalse
                                    1 -> instr.ifTrue
                                    else -> throw IllegalArgumentException("branch value must be 0 or 1, was ${inst.value}")
                                }
                            }
                            is Jump -> instr.target
                            is Exit -> return null
                            is Return -> {
                                val retValue = values.getInst(instr.value)
                                require(retValue.type == function.returnType) {
                                    "return type must match, ${retValue.type} != ${function.returnType}"
                                }
                                return retValue
                            }
                        }

                        currBlock = nextBlock
                        null
                    }
                }

                if (result == null) {
                    if (instr.users.isNotEmpty())
                        values[instr] = UnitInst
                } else {
                    values[instr] = result
                }

                steps++
            }
        }
    }

    private val states = sequence<State> {
        callFunction(program.entry, emptyList(), emptyList())
    }.iterator()

    fun step(): State = states.next()

    fun isDone(): Boolean = !states.hasNext()

    fun runToEnd(): State = states.asSequence().last()
}

private fun ValuesMap.getInst(key: Value, type: Type? = null): ValueInst {
    val inst = when (key) {
        is UnitValue -> UnitInst
        is Constant -> IntegerInst(key.type, key.value)
        else -> this[key] ?: error("No valueInst found for $key")
    }

    if (type != null)
        require(type == inst.type)
    return inst
}