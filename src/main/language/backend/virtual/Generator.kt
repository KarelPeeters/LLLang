package language.backend.virtual

import language.backend.virtual.ConstOrReg.Const
import language.backend.virtual.ConstOrReg.Reg
import language.ir.AggregateValue
import language.ir.Alloc
import language.ir.BasicBlock
import language.ir.BasicInstruction
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
import language.ir.Terminator
import language.ir.UnaryOp
import language.ir.UndefinedValue
import language.ir.VoidType
import language.ir.VoidValue
import language.ir.Value
import language.ir.visitors.ValueVisitor
import language.util.Graph
import language.util.TraverseOrder.DepthFirst
import language.util.reachable

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

    private fun generateProgram(program: Program): VProgram {
        funcAddresses.putAll(program.functions.associateWith { Const(0) })

        //put the entry function first, so no initial call is neccesary
        //entry returns will be replaced by Exit instrucions
        appendFunction(program.entry)
        for (function in program.functions) {
            if (!function.isEntryFunction())
                appendFunction(function)
        }

        funcAddresses.clear()
        return VProgram(result)
    }

    private fun appendFunction(function: Function) {
        blockAddresses.putAll(function.blocks.associateWith { Const(0) })
        funcAddresses.getValue(function).value = nextAdress() - 1

        if (function.parameters.isNotEmpty()) TODO("parameters")
        if (function.returnType != VoidType) TODO("return value")

        //order blocks so the ifFalse block is behind the jump as often as possible
        //also puts the entry first, so no initial jump neccesary
        val orderedBlocks = object : Graph<BasicBlock> {
            override val roots = listOf(function.entry)
            override fun children(node: BasicBlock) = when (val term = node.terminator) {
                is Branch -> listOf(term.ifFalse, term.ifTrue)
                else -> node.successors()
            }
        }.reachable(DepthFirst).toList()

        for ((i, block) in orderedBlocks.withIndex()) {
            appendBlock(block, orderedBlocks.getOrNull(i + 1))
        }

        blockAddresses.clear()
        registerAlloc.clear()
        nextRegIndex = 0
    }

    private fun appendBlock(block: BasicBlock, nextBlock: BasicBlock?) {
        blockAddresses.getValue(block).value = nextAdress() - 1

        for (instr in block.basicInstructions) {
            appendBasicInstruction(instr)
        }

        //store into phi registers
        for (phi in block.successors().flatMap { it.phis() }) {
            val source = phi.sources.getValue(block).asV() ?: continue
            append(VInstruction.Put(phi.asV(), source))
        }

        appendTerminator(block.terminator, nextBlock)
    }

    private fun appendBasicInstruction(instr: BasicInstruction) {
        when (instr) {
            is Alloc -> TODO("alloc, keep track of original sp at start of function if dynamic")
            is Store -> {
                val address = instr.pointer.asV() ?: return
                val value = instr.value.asV() ?: return
                append(VInstruction.Store(AddressCalc(address), value))
            }
            is Load -> {
                val address = instr.pointer.asV() ?: return
                append(VInstruction.Load(AddressCalc(address), instr.asV()))
            }
            is BinaryOp -> {
                val left = instr.left.asV() ?: return
                val right = instr.right.asV() ?: return
                append(VInstruction.Binary(instr.opType, instr.asV(), left, right))
            }
            is UnaryOp -> {
                val value = instr.value.asV() ?: return
                append(VInstruction.Unary(instr.opType, instr.asV(), value))
            }
            is Phi -> Unit //do nothing, this is handled at the block level
            is Eat -> Unit //do nothing
            is Blur -> {
                //TODO avoid register allocation for blur altogether instead?
                val value = instr.value.asV() ?: return
                append(VInstruction.Put(instr.asV(), value))
            }
            is Call -> {
                if (instr.arguments.isNotEmpty()) TODO("arguments")
                if (instr.type != VoidType) TODO("return value")

                //TODO optimize this as part of real register allocation
                //push currently used registers to stack
                val regs = registerAlloc.values.toList()
                for (reg in regs)
                    append(VInstruction.Push(reg))

                //return to the Jump instruction which then runs the instruction after that
                append(VInstruction.Push(Const(nextAdress() + 1)))
                appendJump(instr.target, null)

                //pop registers again in reversed order
                for (reg in regs.asReversed())
                    append(VInstruction.Pop(reg))
            }
            is GetSubValue.Struct -> TODO()
            is GetSubValue.Array -> TODO()
            is GetSubPointer.Array -> TODO()
            is GetSubPointer.Struct -> TODO()
            is AggregateValue -> TODO()
        }
    }

    private fun appendTerminator(term: Terminator, nextBlock: BasicBlock?) {
        when (term) {
            is Branch -> when (val cond = term.value.asV()) {
                //const condition not allowed
                is Const -> {
                    val target = if (cond.value == 0) term.ifFalse else term.ifTrue
                    appendJump(target, nextBlock)
                }
                is Reg -> {
                    val target = term.ifTrue.asV() ?: return
                    append(VInstruction.Jump(target, cond))
                    appendJump(term.ifFalse, nextBlock)
                }
                null -> Unit
            }
            is Jump -> {
                appendJump(term.target, nextBlock)
            }
            is Exit -> {
                append(VInstruction.Exit)
            }
            is Return -> {
                if (term.function.isEntryFunction()) {
                    append(VInstruction.Exit)
                    return
                }

                if (term.value.type != VoidType) TODO("return value")

                append(VInstruction.Pop(Reg.PC))
            }
        }
    }

    private fun appendJump(target: Value, nextBlock: BasicBlock?) {
        if (target != nextBlock)
            append(VInstruction.Jump(target.asV() ?: return, null))
    }

    private fun append(instr: VInstruction) {
        result += instr
    }

    private fun Instruction.asV(): Reg = converter(this)
    /** Returns the [Value] as a [ConstOrReg], `null` represents an [UndefinedValue]. */
    private fun Value.asV() = converter(this)

    private val converter = VConverter()

    private inner class VConverter : ValueVisitor<ConstOrReg?> {
        override fun invoke(value: Function) = funcAddresses.getValue(value)
        override fun invoke(value: BasicBlock) = blockAddresses.getValue(value)

        override fun invoke(value: Instruction): Reg = registerAlloc.getOrPut(value) { Reg.General(nextRegIndex++) }
        override fun invoke(value: ParameterValue) = TODO("parameters")

        override fun invoke(value: Constant): Const = Const(value.value)
        override fun invoke(value: UndefinedValue): Nothing? = null
        override fun invoke(value: VoidValue) = error("use of UnitValue")
    }
}
