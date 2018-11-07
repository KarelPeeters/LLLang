package language.interpreter

import language.ir.Alloc
import language.ir.BasicBlock
import language.ir.BinaryOp
import language.ir.Branch
import language.ir.Constant
import language.ir.Exit
import language.ir.Function
import language.ir.Instruction
import language.ir.IntegerType
import language.ir.IntegerType.Companion.bool
import language.ir.Jump
import language.ir.Load
import language.ir.NameEnv
import language.ir.Phi
import language.ir.Store
import language.ir.Type
import language.ir.UnaryOp
import language.ir.Value
import language.ir.VoidType
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

object VoidInst : ValueInst(VoidType) {
    override fun shortString() = "void"
    override fun toString() = "void"
}

sealed class Current {
    abstract fun fullStr(env: NameEnv): String

    data class Instruction(val instr: language.ir.Instruction) : Current() {
        override fun fullStr(env: NameEnv) = instr.fullStr(env)
    }

    data class Terminator(val term: language.ir.Terminator) : Current() {
        override fun fullStr(env: NameEnv) = term.fullStr(env)
    }

    object Done : Current() {
        override fun fullStr(env: NameEnv) = throw IllegalStateException()
    }
}

data class State(
        val values: Map<Instruction, ValueInst>,
        val current: Current,
        val prevBlock: BasicBlock?,
        val currBlock: BasicBlock?
)

class Interpreter(val function: Function) {
    private val values = mutableMapOf<Instruction, ValueInst>()

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

            for (instr in currBlock.instructions) {
                yield(State(values, Current.Instruction(instr), prevBlock, currBlock))
                execute(instr, prevBlock)
            }

            val term = currBlock.terminator
            yield(State(values, Current.Terminator(term), prevBlock, currBlock))

            val next = when (term) {
                is Branch -> {
                    val inst = getInst(term.value, bool) as IntegerInst
                    when (inst.value) {
                        0 -> term.ifFalse
                        1 -> term.ifTrue
                        else -> throw IllegalArgumentException("branch value must be 0 or 1, was ${inst.value}")
                    }
                }
                is Jump -> term.target
                is Exit -> break@loop
            }
            prevBlock = currBlock
            currBlock = next
        }

        yield(State(values, Current.Done, prevBlock, null))
    }

    fun step(): State = run.next()

    fun runToEnd(): State = run.asSequence().last()

    private fun execute(instr: Instruction, prev: BasicBlock?) {
        val result: ValueInst = when (instr) {
            is Alloc -> {
                require(instr !in values) { "alloc instructions can only run once" }
                BoxInst(instr.type, null)
            }
            is Store -> {
                val box = getInst(instr.pointer) as BoxInst
                box.value = getInst(instr.value)
                VoidInst
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
                getInst(instr.sources[prev!!]!!)
            }
        }
        if (result != VoidInst)
            values[instr] = result
    }
}

