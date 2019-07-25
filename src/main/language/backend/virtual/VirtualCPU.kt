package language.backend.virtual

import language.backend.virtual.ConstOrReg.Const
import language.backend.virtual.ConstOrReg.Reg
import language.ir.BinaryOpType
import language.ir.Constant
import language.ir.IntegerType.Companion.i32
import language.ir.UnaryOpType
import kotlin.math.max
import kotlin.math.min

class VProgram(val instructions: List<VInstruction>) {
    override fun toString() = instructions.withIndex().joinToString("\n", postfix = "\n") { (i, instr) -> "$i\t$instr" }
}

class VirtualCPU(val numRegs: Int) {
    val memory = IntTape()

    val regContents: MutableMap<Reg, Int> = mutableMapOf()
    val generalRegs: List<Reg.General> = List(numRegs) { Reg.General(it) }

    init {
        regContents[Reg.PC] = 0
        regContents[Reg.SP] = -1
        regContents.putAll(generalRegs.map { it to 0 })
    }

    var pc
        get() = Reg.PC.get()
        set(value) {
            Reg.PC.set(value)
        }

    var sp
        get() = Reg.SP.get()
        set(value) {
            Reg.SP.set(value)
        }

    fun printState() {
        println((listOf(Reg.PC, Reg.SP) + generalRegs).joinToString("\n") { reg ->
            "$reg\t${regContents.getValue(reg)}"
        } + "\n$memory")
    }

    fun run(program: VProgram) {
        val instructions = program.instructions

        while (true) {
            if (pc !in instructions.indices)
                error("instruction runoff: $pc !in ${instructions.indices}")

            when (val instr = instructions[pc]) {
                is VInstruction.Put -> instr.target.set(instr.value.get())
                is VInstruction.Binary -> instr.target.set(
                        instr.type.calculate(
                                Constant(i32, instr.left.get()),
                                Constant(i32, instr.right.get())
                        ).value
                )
                is VInstruction.Unary -> instr.target.set(
                        instr.type.calculate(
                                Constant(i32, instr.value.get())
                        ).value
                )
                is VInstruction.Jump -> {
                    val take = instr.cond?.let { it.get() != 0 } ?: true
                    if (take)
                        pc = instr.target.get()
                    Unit
                }
                VInstruction.Exit -> return
                is VInstruction.Push -> setMemory(sp--, instr.value.get())
                is VInstruction.Pop -> instr.target.set(getMemory(++sp))
                is VInstruction.Load -> instr.target.set(getMemory(instr.address.get()))
                is VInstruction.Store -> setMemory(instr.address.get(), instr.value.get())
            }.also { }

            pc++
        }
    }

    private fun getMemory(address: Int): Int {
        if (address == 0) throw NullPointerException()
        return memory[address]
    }

    private fun setMemory(address: Int, value: Int) {
        if (address == 0) throw NullPointerException()
        memory[address] = value
    }

    private fun Reg.set(value: Int) {
        regContents[this] = value
    }

    private fun Reg.get(): Int {
        return regContents[this] ?: throw IllegalRegException("this CPU doesn't have register $this")
    }

    private fun ConstOrReg.get(): Int {
        return when (this) {
            is Const -> this.value
            is Reg -> this.get()
        }
    }
}

/**
 * List-like structure that allows negative indiches and random access beyond the current bounds.
 */
class IntTape(val default: Int = 0) {
    var pos = IntArray(10) { default }
    var neg = IntArray(10) { default }

    var indexExtremes: Pair<Int, Int>? = null

    operator fun set(index: Int, value: Int) {
        if (index >= 0) {
            pos = alloc(pos, index)
            pos[index] = value
        } else {
            neg = alloc(neg, -index - 1)
            neg[-index - 1] = value
        }

        indexExtremes = indexExtremes?.let { (mi, ma) -> min(mi, index) to max(ma, index) } ?: index to index
    }

    operator fun get(index: Int): Int = when {
        index >= 0 -> pos.getOrElse(index) { default }
        else -> neg.getOrElse(-index - 1) { default }
    }

    private fun alloc(arr: IntArray, maxIndex: Int): IntArray = when (maxIndex) {
        in arr.indices -> arr
        else -> {
            arr.copyInto(IntArray(max(2 * arr.size, maxIndex + 1)) { default })
        }
    }

    override fun toString(): String {
        val (mi, ma) = indexExtremes ?: return "empty"

        val indexBuilder = StringBuilder()
        val valueBuilder = StringBuilder()

        for (i in mi..ma) {
            val index = i.toString()
            val value = " " + get(i).toString()

            val length = max(index.length, value.length) + 1
            indexBuilder.append(index.padEnd(length))
            valueBuilder.append(value.padEnd(length))
        }

        indexBuilder.appendln()
        indexBuilder.append(valueBuilder)
        return indexBuilder.toString()
    }
}

sealed class ConstOrReg {
    sealed class Reg : ConstOrReg() {
        object PC : Reg() {
            override fun toString() = "pc"
        }

        object SP : Reg() {
            override fun toString() = "sp"
        }

        data class General(val index: Int) : Reg() {
            override fun toString() = "r${'a' + index}"
        }
    }

    class Const(var value: Int) : ConstOrReg() {
        override fun toString() = "Const($value)"
    }
}

class IllegalRegException(msg: String) : Exception(msg)

sealed class VInstruction {
    //basic
    class Put(val target: Reg, val value: ConstOrReg) : VInstruction() {
        override fun toString() = "put $target $value"
    }

    class Binary(
            val type: BinaryOpType,
            val target: Reg,
            val left: ConstOrReg,
            val right: ConstOrReg
    ) : VInstruction() {
        override fun toString() = "${type.toString().toLowerCase()} $target $left $right"
    }

    class Unary(
            val type: UnaryOpType,
            val target: Reg,
            val value: ConstOrReg
    ) : VInstruction() {
        override fun toString() = "${type.toString().toLowerCase()} $target $value"
    }

    //memory
    class Load(val address: ConstOrReg, val target: Reg) : VInstruction() {
        override fun toString() = "load $address, $target"
    }

    class Store(val address: ConstOrReg, val value: ConstOrReg) : VInstruction() {
        override fun toString() = "store $address $value"
    }

    //control flow
    class Jump(val target: ConstOrReg, val cond: Reg? = null) : VInstruction() {
        override fun toString() = if (cond == null) "jump $target" else "jump $target $cond"
    }

    object Exit : VInstruction() {
        override fun toString() = "exit"
    }

    //stack
    class Push(val value: ConstOrReg) : VInstruction() {
        override fun toString() = "push $value"
    }

    class Pop(val target: Reg) : VInstruction() {
        override fun toString() = "pop $target"
    }
}
