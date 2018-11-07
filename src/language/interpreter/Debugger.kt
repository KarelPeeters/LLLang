package language.interpreter

import language.ir.BasicBlock
import language.ir.Function
import language.ir.NameEnv
import kotlin.math.max

const val ANSI_RESET = "\u001B[0m"
const val ANSI_RED = "\u001B[31m"
const val ANSI_GREEN = "\u001B[32m"
const val ANSI_BLUE = "\u001B[34m"
const val ANSI_GRAY = "\u001B[37m"

val WIDTH_REGEX = """w ([+-]?)(\d+)""".toRegex()

class Debugger(function: Function, val env: NameEnv = NameEnv()) {
    private val interpreter = Interpreter(function)
    private val breakPoints = mutableSetOf<Current>()

    private var state = interpreter.step()
    private var width = 80

    fun start() {
        loop@ while (true) {
            render()

            val line = readLine()
            when (line) {
                "q", null -> break@loop
                "" -> if (!done()) step()
                "b" -> breakPoints.toggle(state.current)
                "c" -> while (!done()) {
                    step()
                    if (atBreakPoint())
                        break
                }
                "p" -> TODO("print entire program")
                else -> {
                    val match = WIDTH_REGEX.matchEntire(line)
                    if (match != null) {
                        val (_, sign, numberStr) = match.groupValues
                        val number = numberStr.toInt()
                        width = when (sign) {
                            "+" -> width + number
                            "-" -> width - number
                            "" -> number
                            else -> throw IllegalStateException()
                        }
                    } else {
                        println("${ANSI_RED}Unknown command '$line'$ANSI_RESET")
                    }
                }
            }
        }
    }

    private fun step() {
        state = interpreter.step()
    }

    private fun atBreakPoint() = state.current in breakPoints

    private fun done() = state.current is Current.Done

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
        }.toList().asReversed()

        val variableNames = state.values.map { it.key.str(env) }.asReversed()
        val variableValues = state.values.map { it.value.shortString() }.asReversed()

        val maxValueWidth = variableValues.map { it.length }.max() ?: 0
        val maxNameWidth = variableNames.map { it.length }.max() ?: 0

        val lineCount = max(prgmLines.size, variableNames.size)
        val lines = (0 until lineCount).map { i ->
            (prgmLines.getOrNull(i) ?: "").ansiPadEnd(width - maxValueWidth - maxNameWidth - 2) +
            (variableNames.getOrNull(i) ?: "").ansiPadEnd(maxNameWidth + 2) +
            (variableValues.getOrNull(i) ?: "").ansiPadEnd(maxValueWidth)
        }.asReversed()

        val result = "\n\n" + lines.joinToString("\n") + "\n${ANSI_BLUE}dbg> $ANSI_RESET"
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

}

private fun String.ansiPadEnd(length: Int, padChar: Char = ' '): String {
    val currentLength = this.replace("\u001B\\[[;\\d]*m".toRegex(), "").length
    return this + padChar.toString().repeat(max(length - currentLength, 0))
}

private fun <E> MutableSet<in E>.toggle(element: E) {
    if (element in this) this -= element
    else this += element
}