package language.optimizer.passes

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
import language.ir.UndefinedValue
import language.ir.Value
import language.optimizer.FunctionPass
import language.optimizer.OptimizerContext
import language.optimizer.passes.LatticeState.*
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

private class SCCPImpl {
    /** Keys can be [Instruction] or [Function] */
    val lattice = mutableMapOf<Value, LatticeState>()

    val executableEdges = mutableSetOf<FlowEdge>()
    val executableBlocks = mutableSetOf<BasicBlock>()

    val queue: Queue<Any> = ArrayDeque()

    constructor(function: Function) {
        updateExecutableEdge(null, function.entry)
    }

    fun run() {
        computeLattice()
        replaceValues()
    }

    private fun computeLattice() {
        while (queue.isNotEmpty()) {
            when (val curr = queue.poll()) {
                is BasicBlock -> visitBlock(curr)
                is Instruction -> visitInstruction(curr)
                else -> error("unexpected queue item ${curr::class}")
            }
        }
    }

    private fun replaceValues() {
        for ((value, state) in lattice) {
            if (state is Known)
                value.replaceWith(state.value)
        }
    }

    private fun lattice(value: Value) = when (value) {
        is Constant -> Known(value)
        is Instruction, is Function -> lattice.getValue(value)
        is UndefinedValue -> Unknown
        else -> Variable
    }

    private fun updateExecutableEdge(from: BasicBlock?, to: BasicBlock) {
        if (executableEdges.add(FlowEdge(from, to))) {
            queue.addAll(to.phis())

            if (executableBlocks.add(to))
                queue += to
        }
    }

    private fun updateLattice(value: Value, state: LatticeState) {
        if (lattice.put(value, state) != state) {
            for (user in value.users) {
                if (user is Instruction)
                    queue += user
            }
        }
    }

    private fun visitBlock(block: BasicBlock) {
        for (instr in block.instructions) {
            visitInstruction(instr)
        }
    }

    private fun visitInstruction(instr: Instruction) {
        when (instr) {
            is Terminator -> visitTerminator(instr)
            is BasicInstruction -> {
                val result = computeInstructionResult(instr) ?: return
                updateLattice(instr, result)
            }
        }
    }

    private fun visitTerminator(instr: Terminator) {
        if (instr is Branch) {
            when (val value = lattice(instr.value)) {
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

    /**
     * Compute the result of an instruction based on [lattice], [executableEdges].
     * This function contains the actual constant folding logic and tries to return the simplest result possible.
     */
    private fun computeInstructionResult(instr: BasicInstruction): LatticeState? = when (instr) {
        is Alloc, is Load, is Call, is Blur -> Variable
        is GetSubPointer.Array, is GetSubPointer.Struct -> Variable
        is AggregateValue -> Variable

        is Store, is Eat -> null

        is Phi -> {
            instr.sources.asSequence().map { (block, value) ->
                if (FlowEdge(block, instr.block) in executableEdges)
                    lattice(value)
                else
                    Unknown
            }.merge()
        }

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
                    is GetSubValue.Struct -> instr.index
                    is GetSubValue.Array -> {
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
}

private data class FlowEdge(val from: BasicBlock?, val to: BasicBlock)

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
