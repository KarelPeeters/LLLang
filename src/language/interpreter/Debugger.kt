package language.interpreter

import language.ir.BasicBlock
import language.ir.Function
import language.ir.NameEnv

const val ANSI_RESET = "\u001B[0m"
const val ANSI_RED = "\u001B[31m"
const val ANSI_GREEN = "\u001B[32m"
const val ANSI_BLUE = "\u001B[34m"
const val ANSI_GRAY = "\u001B[37m"

class Debugger(function: Function, val env: NameEnv = NameEnv()) {
    private val interpreter = Interpreter(function)
    private val breakPoints = mutableSetOf<Current>()
    private var state = interpreter.step()

    fun start() {
        loop@ while (true) {
            render()

            when (readLine()) {
                "q", null -> break@loop
                "" -> if (state.current != Current.Done) step()
                "b" -> breakPoints += state.current
                "r" -> breakPoints -= state.current
                "c" -> do step() while (!hitBreakPoint())
                "p" -> TODO("print entire program")
            }
        }
    }

    private var width = 50
    private var height = 30

    private fun render() {
        val state = state

        val prgmLines = sequence<String> {
            if (state.prevBlock != null)
                renderBlock(state.prevBlock, ANSI_GRAY)

            if (state.currBlock != null) {
                renderBlock(state.currBlock, "")

                for (succ in state.currBlock.successors())
                    renderBlock(succ, ANSI_GRAY)
            }
        }.toList()

        val result = "\n\n" + prgmLines.joinToString("\n") + "\n${ANSI_BLUE}dbg> $ANSI_RESET"
        print(result)
    }

    private suspend fun SequenceScope<String>.renderBlock(block: BasicBlock, color: String) {
        yield("$color${block.str(env)}$ANSI_RESET")

        val lines = block.instructions.map { Current.Instruction(it) } +
                    Current.Terminator(block.terminator)

        for (instr in lines) {
            yield((if (state.current == instr) "$ANSI_GREEN>$ANSI_RESET" else " ") +
                  (if (breakPoints.any { it == instr }) "$ANSI_RED*$ANSI_RESET" else " ") +
                  " $color${instr.fullStr(env)}$ANSI_RESET")
        }
    }

    private fun step() {
        state = interpreter.step()
    }

    private fun hitBreakPoint() = state.current is Current.Done || state.current in breakPoints

}