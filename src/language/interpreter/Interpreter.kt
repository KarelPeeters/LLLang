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

sealed class ValueInst(val type: Type)

class IntegerInst(type: Type, val value: Int) : ValueInst(type) {
    init {
        require(type is IntegerType)
    }

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

    override fun toString() = "$type [$value]"
}

object VoidInst : ValueInst(VoidType) {
    override fun toString() = "void"
}

class Interpreter {
    private val nameEnv = NameEnv()
    private val values = mutableMapOf<Instruction, ValueInst>()

    private fun getInst(key: Value): ValueInst {
        if (key is Constant)
            return IntegerInst(key.type, key.value)
        return values[key]!!
    }

    fun run(function: Function) {
        println(function.fullStr(nameEnv))

        var prev: BasicBlock? = null
        var current = function.entry

        loop@ while (true) {
            require(current in function.blocks) { "all visited blocks must be listed in function" }

            for (instr in current.instructions) {
                run(instr, prev)
                println(values.toList().joinToString("\n") { (k, v) -> k.str(nameEnv) + " = " + v })
                println()
            }

            val term = current.terminator
            val next = when (term) {
                is Branch -> {
                    val inst = getInst(term.value) as IntegerInst
                    when (inst.value) {
                        0 -> term.ifFalse
                        1 -> term.ifTrue
                        else -> throw IllegalArgumentException("branch value must be 0 or 1, was ${inst.value}")
                    }
                }
                is Jump -> term.target
                is Exit -> break@loop
            }
            prev = current
            current = next
        }
    }

    private fun run(instr: Instruction, prev: BasicBlock?) {
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

