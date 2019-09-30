package language.ir.support

import language.ir.*
import language.ir.Function
import language.ir.IntegerType.Companion.bool
import language.ir.IntegerType.Companion.i32
import language.optimizer.DominatorInfo
import language.util.subListUntil
import kotlin.random.Random

class Fuzzer(val rand: Random) {
    companion object {
        fun generateRandomProgram(rand: Random) = Fuzzer(rand).randomProgram()
    }

    private val basicTypes = mutableListOf(bool, i32, i32.pointer)
    private val functionTypes = mutableListOf<FunctionType>()

    private val functions = mutableListOf<Function>()
    private val placeHolders = mutableListOf<PlaceholderValue>()

    private fun placeholder(type: Type) = PlaceholderValue(null, type).also { placeHolders += it }

    private val randomIntType = WeightedChoice<IntegerType>(
            1 to { bool },
            5 to { i32 }
    )

    private fun randomFunctionType(): FunctionType = functionTypes.random(rand)

    private val randomType = WeightedChoice<Type>(
            1 to { basicTypes.random(rand) },
            1 to { randomFunctionType() }
    )

    private val randomTypeIncVoid = WeightedChoice<Type>(
            3 to { randomType() },
            1 to { VoidType }
    )

    private val randomInstr = WeightedChoice<BasicInstruction>(
            1 to {
                val type = randomType()
                Store(placeholder(type.pointer), placeholder(type))
            },
            1 to { Load(null, placeholder(randomType().pointer)) },
            1 to {
                val type = randomIntType()
                BinaryOp(null, BINARY_OP_TYPES.random(rand), placeholder(type), placeholder(type))
            },
            1 to { UnaryOp(null, UNARY_OP_TYPES.random(rand), placeholder(randomIntType())) },
            1 to { Eat(listOf(placeholder(randomType()))) },
            1 to { Blur(null, placeholder(randomType())) },
            4 to {
                val funcType = randomFunctionType()
                Call(null, placeholder(funcType), funcType.paramTypes.map(this::placeholder))
            }
    )

    private fun randomProgram(): Program {
        val entryFunc = Function("main", emptyList(), VoidType, emptySet())

        //generate functions
        repeat(rand.nextInt(10)) { fi ->
            val params = List(rand.nextInt(5)) { pi -> "p$pi" to randomType() }
            val func = Function("f$fi", params, randomTypeIncVoid(), emptySet())

            functions += func
            functionTypes += func.functionType
        }

        //populate functions
        for (func in listOf(entryFunc) + functions) {
            populateFunction(func)
        }

        //build program
        val program = Program()
        program.entry = entryFunc
        program.addFunction(entryFunc)
        program.addFunctions(functions)

        return program
    }

    private fun populateFunction(func: Function) {
        //generate blocks
        val entryBlock = BasicBlock("entry")
        entryBlock.terminator = Exit()

        val blocks = List(rand.nextInt(10)) { BasicBlock(null) }

        func.entry = entryBlock
        func.add(entryBlock)
        func.addAll(blocks)

        val randomExitTerminator = WeightedChoice<Terminator>(
                1 to { Exit() },
                5 to { Return(placeholder(func.returnType)) }
        )

        val randomTerminator = WeightedChoice<Terminator>(
                1 to { randomExitTerminator() },
                3 to { Jump(blocks.random(rand)) },
                3 to { Branch(placeholder(bool), blocks.random(rand), blocks.random(rand)) }
        )

        //add allocs to entry
        val allocs = List(rand.nextInt(5)) { Alloc(null, randomType()) }
        entryBlock.addAll(allocs)

        //populate blocks
        for (block in listOf(entryBlock) + blocks) {
            //basic instructions
            val instructions = List(rand.nextInt(10)) { randomInstr() }
            block.addAll(instructions)

            //terminator
            block.terminator = if (blocks.isEmpty()) randomExitTerminator() else randomTerminator()
        }

        //add phis
        for (block in blocks) {
            repeat(rand.nextInt(4)) {
                val type = randomType()
                val phi = Phi(null, type)
                phi.sources.putAll(block.predecessors().associateWith { placeholder(type) })
                block.add(0, phi)
            }
        }

        //Replace placeholders with actual values
        val domInfo = DominatorInfo(func)

        for (placeholder in placeHolders) {
            val instr = placeholder.users.single() as Instruction
            placeholder.replaceWith(randomDominatingValue(placeholder.type, instr, domInfo))
        }
        placeHolders.clear()
    }

    private fun randomDominatingValue(type: Type, user: Instruction, domInfo: DominatorInfo): Value {
        if (type == VoidType) return VoidValue

        val candInstructions = getDominatingInstructions(user, domInfo).filter { it.type == type }
        val candParams = user.function.parameters.filter { it.type == type }
        val candFunctions = functions.filter { it.type == type }

        return WeightedChoice<Value>(
                4 to pickRandIfNotEmpty(candInstructions),
                4 to pickRandIfNotEmpty(candParams),
                4 to pickRandIfNotEmpty(candFunctions),
                1 to { UndefinedValue(type) },
                4 to pickConstIfInteger(type)
        )()
    }

    private fun getDominatingInstructions(user: Instruction, domInfo: DominatorInfo): MutableList<Instruction> {
        val result = mutableListOf<Instruction>()
        for (block in domInfo.strictDominators(user.block))
            result.addAll(block.basicInstructions)
        result.addAll(user.block.instructions.subListUntil(user))
        return result
    }

    private fun <T> pickRandIfNotEmpty(list: List<T>): (() -> T)? =
            if (list.isEmpty()) null else ({ list.random(rand) })

    private fun pickConstIfInteger(type: Type): (() -> Constant)? = when (type) {
        bool -> ({ Constant(bool, rand.nextInt(2)) })
        i32 -> ({ Constant(i32, rand.nextInt(100) - 50) })
        is IntegerType -> error("general integer type not yet supported")
        else -> null
    }

    /**
     * Utility class that calls a random passed lambda with probabilties propertional to the given weights.
     * If either a or its returned value is ´null´ that option is skipped and another one is tried.
     */
    private inner class WeightedChoice<T : Any>(vararg options: Pair<Int, (() -> T)?>) {
        @Suppress("UNCHECKED_CAST")
        val options = options.filter { it.second != null } as List<Pair<Int, () -> T>>
        val sum = this.options.sumBy { it.first }

        init {
            check(options.any { it.second != null })
            check(options.all { it.first > 0 })
        }

        operator fun invoke(): T {
            val target = rand.nextInt(sum)
            var curr = 0
            for ((chance, block) in options) {
                curr += chance
                if (curr >= target) {
                    return block()
                }
            }

            throw IllegalStateException()
        }
    }
}