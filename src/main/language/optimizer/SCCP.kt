package language.optimizer

import language.ir.AggregateValue
import language.ir.Alloc
import language.ir.BasicBlock
import language.ir.BinaryOp
import language.ir.Blur
import language.ir.Branch
import language.ir.Call
import language.ir.Constant
import language.ir.Eat
import language.ir.Function
import language.ir.GetSubPointer
import language.ir.GetSubValue
import language.ir.Instruction
import language.ir.IntegerType
import language.ir.Load
import language.ir.Phi
import language.ir.Store
import language.ir.Terminator
import language.ir.UnaryOp
import language.ir.Value
import language.optimizer.LatticeState.*
import java.util.*

/**
 * _Sparse Conditional Constant Propagation_, a pass that finds constants within a function taking into account which
 * blocks and branches are possible to execute.
 */
object SCCP : FunctionPass() {
    override fun OptimizerContext.optimize(function: Function) {
        SCCPImpl(function).run()
    }
}

private class SCCPImpl(val function: Function) {
    val lattice = mutableMapOf<Instruction, LatticeState>()
    val executableEdges = mutableSetOf<FlowEdge>()
    val executableBlocks = mutableSetOf<BasicBlock>()

    val blockQueue: Queue<BasicBlock> = ArrayDeque()
    val instrQueue: Queue<Instruction> = ArrayDeque()

    init {
        for (instr in function.blocks.flatMap { it.instructions }) {
            if (instr !is Terminator && instr !is Eat && instr !is Store)
                lattice[instr] = Unknown
        }

        updateExecutableEdge(null, function.entry)
    }

    private fun valueToLattice(value: Value) = when (value) {
        is Constant -> Known(value)
        is Instruction -> lattice.getValue(value)
        else -> Variable
    }

    private fun updateLattice(instr: Instruction, value: LatticeState) {
        if (lattice.put(instr, value) != value) {
            for (user in instr.users) {
                if (user is Instruction)
                    instrQueue += user
            }
        }
    }

    private fun updateExecutableEdge(from: BasicBlock?, to: BasicBlock) {
        if (executableEdges.add(FlowEdge(from, to))) {
            instrQueue += to.phis()

            if (executableBlocks.add(to))
                blockQueue += to
        }
    }

    private fun visitTerminator(instr: Terminator) {
        if (instr is Branch) {
            when (val value = valueToLattice(instr.value)) {
                Unknown -> error("can't happen, value was just lowered")
                is Known -> {
                    check(value.value.type == IntegerType.bool)
                    if (value.value.value == 0)
                        updateExecutableEdge(instr.block, instr.ifFalse)
                    else
                        updateExecutableEdge(instr.block, instr.ifTrue)
                }
                Variable -> {
                    updateExecutableEdge(instr.block, instr.ifTrue)
                    updateExecutableEdge(instr.block, instr.ifFalse)
                }
            }
        } else {
            for (target in instr.targets())
                updateExecutableEdge(instr.block, target)
        }
    }

    private fun calculatePhiResult(instr: Phi): LatticeState {
        return instr.sources.asSequence().map { (block, value) ->
            if (FlowEdge(block, instr.block) in executableEdges)
                valueToLattice(value)
            else
                Unknown
        }.merge()
    }

    fun visitInstruction(instr: Instruction) {
        if (instr is Terminator) {
            visitTerminator(instr)
        } else {
            val result = if (instr is Phi) {
                calculatePhiResult(instr)
            } else {
                computeInstructionResult(instr, this::valueToLattice) ?: return
            }

            updateLattice(instr, result)
        }
    }

    fun visitBlock(block: BasicBlock) {
        for (instr in block.instructions) {
            visitInstruction(instr)
        }
    }

    fun computeLattice() {
        while (true) {
            val block = blockQueue.poll()
            if (block != null) {
                visitBlock(block)
                continue
            }

            val instr = instrQueue.poll()
            if (instr != null) {
                visitInstruction(instr)
                continue
            }

            break
        }
    }

    fun replaceValues() {
        for ((value, state) in lattice) {
            if (state is Known)
                value.replaceWith(state.value)
        }
    }

    fun run() {
        computeLattice()
        replaceValues()
    }
}

data class FlowEdge(val from: BasicBlock?, val to: BasicBlock)

/**
 * A lattice state, see subclass docs for the meaning of each state.
 * Subclasses of this class should properly implement [equals].
 */
private sealed class LatticeState {
    /** Assumed to be constant but no known value yet */
    object Unknown : LatticeState()

    /** Assumed to be constant with the given [value] */
    data class Known(val value: Constant) : LatticeState()

    /** Known to not be constant */
    object Variable : LatticeState()
}

/**
 * Short circuiting merging of LatticeStates
 */
private fun Sequence<LatticeState>.merge(): LatticeState {
    var current: LatticeState = Unknown

    for (state in this) {
        if (state is Variable) return Variable
        if (state is Known && current is Known) {
            if (state.value != current.value)
                return Variable
        }
        if (state is Known)
            current = state
    }

    return current
}

/**
 * Compute the result of an instruction as a [LatticeState] where the operands' [LatticeState]s can be determined by
 * calling [lattice] with the operand value.
 *
 * This function contains the actual constant folding logic and tries to return the simplest result possible.
 */
private inline fun computeInstructionResult(instr: Instruction, lattice: (Value) -> LatticeState): LatticeState? = when (instr) {
    is Alloc, is Load, is Call, is Blur -> Variable
    is GetSubPointer.Array, is GetSubPointer.Struct -> Variable
    is AggregateValue -> Variable

    is Store, is Eat -> null

    is Terminator, is Phi -> error("should be handled elsewhere")

    is BinaryOp -> {
        val left = lattice(instr.left)
        val right = lattice(instr.right)

        if (left == Variable || right == Variable)
            Variable
        else if (left is Known && right is Known)
            Known(instr.opType.calculate(left.value, right.value))
        else
            Unknown
    }

    is UnaryOp -> {
        when (val value = lattice(instr.value)) {
            Unknown -> Unknown
            is Known -> Known(instr.opType.calculate(value.value))
            Variable -> Variable
        }
    }

    is GetSubValue -> {
        val target = instr.target
        if (target is AggregateValue) {
            val fixedIndex = when (instr) {
                is GetSubValue.GetStructValue -> instr.index
                is GetSubValue.GetArrayValue -> {
                    val index = lattice(instr.index)
                    (index as? Known)?.value?.value
                }
            }

            if (fixedIndex != null)
                lattice(target.values[fixedIndex])
            else
                Variable
        } else
            Variable
    }
}