package language.backend.virtual

import language.backend.virtual.ConstOrReg.Const
import language.backend.virtual.ConstOrReg.Reg
import language.ir.AggregateValue
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
import language.ir.GetSubPointer
import language.ir.GetSubValue
import language.ir.Instruction
import language.ir.Jump
import language.ir.Load
import language.ir.ParameterValue
import language.ir.Phi
import language.ir.Program
import language.ir.Return
import language.ir.Store
import language.ir.UnaryOp
import language.ir.UndefinedValue
import language.ir.UnitType
import language.ir.UnitValue
import language.ir.Value
import language.ir.visitors.ValueVisitor

class Generator private constructor() {
    companion object {
        fun generate(program: Program): VProgram = Generator().generateProgram(program)
    }

    private val result = mutableListOf<VInstruction>()
    /** The address of the next appended instruction */
    private fun nextAdress() = result.size

    private val funcAddresses = mutableMapOf<Function, Const>()
    private val blockAddresses = mutableMapOf<BasicBlock, Const>()

    private var nextRegIndex = 0
    private val registerAlloc = mutableMapOf<Instruction, Reg>()

    private fun append(instr: VInstruction) {
        result += instr
    }

    private fun generateProgram(program: Program): VProgram {
        funcAddresses.putAll(program.functions.associateWith { Const(0) })

        appendInstruction(Call(null, program.entry, emptyList()))
        append(VInstruction.Exit)

        for (function in program.functions) {
            appendFunction(function)
        }

        funcAddresses.clear()
        return VProgram(result)
    }

    private fun appendFunction(function: Function) {
        blockAddresses.putAll(function.blocks.associateWith { Const(0) })
        funcAddresses.getValue(function).value = nextAdress() - 1

        if (function.parameters.isNotEmpty()) TODO("parameters")
        if (function.returnType != UnitType) TODO("return value")

        for (block in function.blocks) {
            appendBlock(block)
        }

        blockAddresses.clear()
        registerAlloc.clear()
        nextRegIndex = 0
    }

    private fun appendBlock(block: BasicBlock) {
        blockAddresses.getValue(block).value = nextAdress() - 1

        for (instr in block.instructions) {
            appendInstruction(instr)
        }
    }

    private fun appendInstruction(instr: Instruction) {
        when (instr) {
            is Alloc -> TODO("alloc, keep track of original sp at start of function if dynamic")
            is Store -> {
                append(VInstruction.Store(instr.pointer.asV(), instr.value.asV()))
            }
            is Load -> {
                append(VInstruction.Load(instr.pointer.asV(), instr.asV()))
            }
            is BinaryOp -> {
                append(VInstruction.Binary(instr.opType, instr.asV(), instr.left.asV(), instr.right.asV()))
            }
            is UnaryOp -> {
                append(VInstruction.Unary(instr.opType, instr.asV(), instr.value.asV()))
            }
            is Phi -> TODO("phi, this needs to happen be assigned a register function-globally")
            is Eat -> Unit //do nothing
            is Blur -> {
                //TODO avoid register allocation for blur altogether instead?
                append(VInstruction.Put(instr.asV(), instr.value.asV()))
            }
            is Call -> {
                if (instr.arguments.isNotEmpty()) TODO("arguments")
                if (instr.type != UnitType) TODO("return value")

                //TODO optimize this as part of real register allocation
                //push currently used registers to stack
                val regs = registerAlloc.values.toList()
                for (reg in regs)
                    append(VInstruction.Push(reg))

                //return to the Jump instruction which then runs the instruction after that
                append(VInstruction.Push(Const(nextAdress() + 1)))
                append(VInstruction.Jump(instr.target.asV()))

                //pop registers again in reversed order
                for (reg in regs.asReversed())
                    append(VInstruction.Pop(reg))
            }
            is GetSubValue.Struct -> TODO()
            is GetSubValue.Array -> TODO()
            is GetSubPointer.Array -> TODO()
            is GetSubPointer.Struct -> TODO()
            is AggregateValue -> TODO()
            is Branch -> {
                when (val cond = instr.value.asV()) {
                    is Const -> {
                        val target = if (cond.value == 0) instr.ifFalse else instr.ifTrue
                        append(VInstruction.Jump(target.asV()))
                    }
                    is Reg -> {
                        append(VInstruction.Jump(instr.ifTrue.asV(), cond))
                        append(VInstruction.Jump(instr.ifFalse.asV()))
                    }
                }
            }
            is Jump -> {
                append(VInstruction.Jump(instr.target.asV()))
            }
            is Exit -> {
                append(VInstruction.Exit)
            }
            is Return -> {
                if (instr.value.type != UnitType) TODO("return value")

                append(VInstruction.Pop(Reg.PC))
            }
        }
    }

    private fun Instruction.asV(): Reg = converter(this)
    private fun Value.asV() = converter(this)

    private val converter = VConverter()

    private inner class VConverter : ValueVisitor<ConstOrReg> {
        override fun invoke(value: Function) = funcAddresses.getValue(value)
        override fun invoke(value: BasicBlock) = blockAddresses.getValue(value)

        override fun invoke(value: Instruction): Reg = registerAlloc.getOrPut(value) { Reg.General(nextRegIndex++) }
        override fun invoke(value: ParameterValue) = TODO("parameters")

        override fun invoke(value: Constant): Const = Const(value.value)
        override fun invoke(value: UndefinedValue) = TODO("undefined")
        override fun invoke(value: UnitValue) = error("use of UnitValue")
    }
}