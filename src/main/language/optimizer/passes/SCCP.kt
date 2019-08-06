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
import language.ir.Load
import language.ir.ParameterValue
import language.ir.Phi
import language.ir.Program
import language.ir.Return
import language.ir.Store
import language.ir.Terminator
import language.ir.Type
import language.ir.UnaryOp
import language.ir.UndefinedValue
import language.ir.Value
import language.ir.VoidValue
import language.ir.visitors.ValueVisitor
import language.optimizer.FunctionPass
import language.optimizer.OptimizerContext
import language.optimizer.ProgramPass
import language.optimizer.passes.LatticeState.*
import java.util.*

/**
 * _Sparse Conditional Constant Propagation_, a pass that finds constants within a function taking into account which
 * blocks and branches are possible to execute.
 */
object SCCP : FunctionPass() {
    override fun OptimizerContext.optimize(function: Function) {
        SCCPImpl(this, function).run()
    }
}

object ProgramSCCP : ProgramPass() {
    override fun OptimizerContext.optimize(program: Program) {
        SCCPImpl(this, program).run()
    }
}

private class SCCPImpl private constructor(val context: OptimizerContext, val multiFunc: Boolean) {
    /** Keys can be [Instruction] or [ParameterValue] */
    val latticeMap = mutableMapOf<Value, LatticeState>().withDefault { Unknown }
    val returnLatticeMap = mutableMapOf<Function, LatticeState>().withDefault { Unknown }

    val executableEdges = mutableSetOf<FlowEdge>()
    val executableBlocks = mutableSetOf<BasicBlock>()

    val queue: Queue<Any> = ArrayDeque()

    constructor(context: OptimizerContext, program: Program) : this(context, multiFunc = true) {
        updateExecutableFunction(program.entry)
    }

    constructor(context: OptimizerContext, function: Function) : this(context, multiFunc = false) {
        updateExecutableFunction(function)
    }

    fun run() {
        computeLattice()
        replaceValues()
    }

    private fun computeLattice() {
        while (queue.isNotEmpty()) {
            when (val curr = queue.poll()) {
                is BasicBlock -> initializeBlock(curr)
                is Instruction -> visitInstruction(curr)
                else -> error("unexpected queue item ${curr::class}")
            }
        }

        if (!multiFunc)
            check(returnLatticeMap.isEmpty()) { "somehow a return value got trached in single function mode" }
    }

    private fun replaceValues() {
        for ((value, state) in latticeMap) {
            val replacement = state.asValue(value.type) ?: continue
            value.replaceWith(replacement)
            context.changed()
        }

        for ((func, state) in returnLatticeMap) {
            val replacement = state.asValue(func.returnType) ?: continue

            for (user in func.users) {
                if (user is Call && user.target == func) {
                    user.replaceWith(replacement)
                }
            }
            context.changed()
        }
    }

    private val lattice = object : ValueVisitor<LatticeState> {
        override fun invoke(value: Function) = Known(value)
        override fun invoke(value: BasicBlock) = Variable
        override fun invoke(value: Instruction) = when (value) {
            is Call -> {
                val target = value.target
                if (multiFunc && target is Function)
                    returnLatticeMap.getValue(target)
                else
                    Variable
            }
            else -> latticeMap.getValue(value)
        }

        override fun invoke(value: ParameterValue) = latticeMap[value] ?: Unknown
        override fun invoke(value: Constant) = Known(value)
        override fun invoke(value: UndefinedValue) = Unknown
        override fun invoke(value: VoidValue) = Variable
    }

    fun updateExecutableEdge(from: BasicBlock, to: BasicBlock) {
        if (executableEdges.add(FlowEdge(from, to))) {
            queue.addAll(to.phis())

            if (executableBlocks.add(to))
                queue += to
        }
    }

    private fun updateExecutableFunction(function: Function) {
        if (executableBlocks.add(function.entry))
            queue += function.entry
    }

    private fun updateMerge(value: Value, state: LatticeState) {
        updateLattice(value, merge(lattice(value), state))
    }

    private fun updateLattice(value: Value, state: LatticeState) {
        val prev = latticeMap.put(value, state)
        check(prev == null || prev >= state)

        if (prev != state) {
            for (user in value.users) {
                if (user is Instruction)
                    queue += user
            }
        }
    }

    private fun initializeBlock(block: BasicBlock) {
        for (instr in block.instructions) {
            visitInstruction(instr)

            if (multiFunc) {
                val operands = if (instr is Call) instr.arguments else instr.operands

                for (op in operands) {
                    if (op is Function) {
                        //the function appears as an operand that's not an immediate call target
                        //be safe and make worst-case assumptions: executable and all parameters variable
                        updateExecutableFunction(op)
                        for (param in op.parameters)
                            updateLattice(param, Variable)
                    }
                }
            }
        }
    }

    private fun visitInstruction(instr: Instruction) {
        when (instr) {
            is Return -> visitReturn(instr)
            is Terminator -> visitControlflowTerminator(instr)
            is Call -> visitCall(instr)
            is BasicInstruction -> {
                val result = computeInstructionResult(instr) ?: return
                updateLattice(instr, result)
            }
        }
    }

    private fun visitReturn(instr: Return) {
        if (multiFunc) {
            val func = instr.block.function

            val prev = returnLatticeMap.getValue(func)
            val next = merge(prev, lattice(instr.value))

            if (returnLatticeMap.put(func, next) != next) {
                //queue users of call instructions with this function as the target
                for (user in func.users) {
                    if (user is Call && user.target == func)
                        queue.addAll(user.users)
                }
            }
        }
    }

    private fun visitCall(instr: Call) {
        if (multiFunc) {
            val target = instr.target
            if (target is Function) {
                updateExecutableFunction(target)
                for ((param, arg) in (target.parameters zip instr.arguments)) {
                    updateMerge(param, lattice(arg))
                }
            }
        } else {
            updateLattice(instr, Variable)
        }
    }

    private fun visitControlflowTerminator(instr: Terminator) {
        if (instr is Branch) {
            when (val value = lattice(instr.value)) {
                Unknown -> error("can't happen, value was just lowered")
                is Known -> {
                    if ((value.value as Constant).value == 0)
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
        is Alloc, is Load, is Blur -> Variable
        is GetSubPointer.Array, is GetSubPointer.Struct -> Variable
        is AggregateValue -> Variable

        is Store, is Eat -> null

        is Call -> error("handled separately")

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
                Known(instr.opType.calculate(left.value as Constant, right.value as Constant))
            else
                Unknown
        }

        is UnaryOp -> {
            when (val value = lattice(instr.value)) {
                Unknown -> Unknown
                is Known -> Known(instr.opType.calculate(value.value as Constant))
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
                        ((index as? Known)?.value as Constant?)?.value
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

private data class FlowEdge(val from: BasicBlock, val to: BasicBlock)

/**
 * A lattice state, see subclass docs for the meaning of each state.
 * Subclasses of this class should properly implement [equals].
 */
private sealed class LatticeState(private val order: Int) {
    abstract fun asValue(type: Type): Value?

    operator fun compareTo(other: LatticeState): Int = this.order.compareTo(other.order)

    /** Assumed to be constant but no known value yet */
    object Unknown : LatticeState(2) {
        override fun asValue(type: Type): Value? = UndefinedValue(type)
    }

    /** Assumed to be constant with the given [value] */
    data class Known(val value: Value) : LatticeState(1) {
        override fun asValue(type: Type): Value? = value.also { check(value.type == type) }
    }

    /** Known to not be constant */
    object Variable : LatticeState(0) {
        override fun asValue(type: Type): Value? = null
    }
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

private fun merge(left: LatticeState, right: LatticeState): LatticeState {
    if (left == right) return left

    if (left == Unknown) return right
    if (right == Unknown) return left

    return Variable
}