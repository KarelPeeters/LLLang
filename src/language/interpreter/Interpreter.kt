package language.interpreter

import language.ir.Alloc
import language.ir.BasicBlock
import language.ir.BinaryOp
import language.ir.Blur
import language.ir.Branch
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
import language.ir.Return
import language.ir.Store
import language.ir.Terminator
import language.ir.Type
import language.ir.UnaryOp
import language.ir.Value
import language.ir.unpoint

sealed class ValueInst(val type: Type) {
    abstract fun shortString(): String
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

data class State(
        val values: Map<Instruction, ValueInst>,
        val current: Instruction?,
        val prevBlock: BasicBlock?
) {
    val currBlock = current?.block
}

class Interpreter(val function: Function) {
    private val values = mutableMapOf<Instruction, ValueInst>()
    var steps = 0
        private set

    private fun getInst(key: Value, type: Type? = null): ValueInst {
        val inst = if (key is Constant)
            IntegerInst(key.type, key.value)
        else
            values[key]!!

        if (type != null)
            require(type == inst.type)
        return inst
    }

    private val run = iterator {
        var prevBlock: BasicBlock? = null
        var currBlock = function.entry

        loop@ while (true) {
            require(currBlock in function.blocks) { "all visited blocks must be listed in function" }

            for ((i, instr) in currBlock.instructions.withIndex()) {
                yield(State(values, instr, prevBlock))

                if (i == currBlock.instructions.lastIndex)
                    require(instr is Terminator) { "BasicBlocks must end with a terminator" }

                val result: ValueInst? = when (instr) {
                    is Alloc -> {
                        require(instr !in values) { "alloc instructions can only run once" }
                        BoxInst(instr.type, null)
                    }
                    is Store -> {
                        val box = getInst(instr.pointer) as BoxInst
                        box.value = getInst(instr.value)
                        null
                    }
                    is Load -> {
                        (getInst(instr.pointer) as BoxInst).value!!
                    }
                    is BinaryOp -> {
                        val leftInst = getInst(instr.left) as IntegerInst
                        val rightInst = getInst(instr.right) as IntegerInst
                        val left = Constant(leftInst.type, leftInst.value)
                        val right = Constant(rightInst.type, rightInst.value)
                        val result = instr.opType.calculate(left, right)
                        IntegerInst(result.type, result.value)
                    }
                    is UnaryOp -> {
                        val valueInst = getInst(instr.value) as IntegerInst
                        val value = Constant(valueInst.type, valueInst.value)
                        val result = instr.opType.calculate(value)
                        IntegerInst(result.type, result.value)
                    }
                    is Phi -> {
                        getInst(instr.sources[prevBlock!!]!!)
                    }
                    is Eat -> {
                        for (operand in instr.operands)
                            getInst(operand)
                        null
                    }
                    is Blur -> {
                        getInst(instr.value)
                    }
                    is Terminator -> {
                        require(i == currBlock.instructions.lastIndex) { "Terminators can only appear at the end of a BasicBlock" }
                        prevBlock = currBlock

                        val nextBlock = when (instr) {
                            is Branch -> {
                                val inst = getInst(instr.value, bool) as IntegerInst
                                when (inst.value) {
                                    0 -> instr.ifFalse
                                    1 -> instr.ifTrue
                                    else -> throw IllegalArgumentException("branch value must be 0 or 1, was ${inst.value}")
                                }
                            }
                            is Jump -> instr.target
                            is Exit -> break@loop
                            is Return -> TODO("return")
                        }

                        currBlock = nextBlock
                        null
                    }
                }
                if (result != null)
                    values[instr] = result
                steps++
            }
        }

        yield(State(values, null, prevBlock))
    }

    fun step(): State = run.next()

    fun runToEnd(): State = run.asSequence().last()
}

