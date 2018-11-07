package language.interpreter

import language.ir.BasicBlock
import language.ir.Function
import language.ir.Instruction
import language.ir.NameEnv
import kotlin.math.max

const val ANSI_RESET = "\u001B[0m"
const val ANSI_RED = "\u001B[31m"
const val ANSI_GREEN = "\u001B[32m"
const val ANSI_BLUE = "\u001B[34m"
const val ANSI_GRAY = "\u001B[37m"

val WIDTH_REGEX = """w ([+-]?)(\d+)""".toRegex()

class Debugger(val function: Function, val env: NameEnv = NameEnv()) {
    private val interpreter = Interpreter(function)
    private val breakPoints = mutableSetOf<Instruction>()

    private var state = interpreter.step()
    private var width = 80

    fun start() {
        renderCode()
        renderPrompt()

        loop@ while (true) {
            val line = readLine()

            when (line) {
                "q", null -> break@loop
                "" -> if (!done()) step()
                "b" -> state.current?.let { breakPoints.toggle(it) }
                "c" -> while (!done()) {
                    step()
                    if (atBreakPoint())
                        break
                }
                //handled during render
                "s", "p" -> Unit
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

            when (line) {
                "s" -> {
                    renderCode()
                    println("steps: ${interpreter.steps}")
                }
                "p" -> renderCode(true)
                else -> renderCode()
            }

            renderPrompt()
        }
    }

    private fun step() {
        state = interpreter.step()
    }

    private fun atBreakPoint() = state.current in breakPoints

    private fun done() = state.current == null

    private fun renderCode(full: Boolean = false) {
        val state = state

        val prgmLines = sequence<String> {
            if (full) {
                for (block in function.blocks)
                    renderBlock(block)
            } else {
                if (state.prevBlock != null)
                    renderBlock(state.prevBlock)

                if (state.currBlock != null) {
                    renderBlock(state.currBlock)
                    for (succ in state.currBlock.successors())
                        renderBlock(succ)
                }
            }
        }.toList().asReversed()

        val variableNames = state.values.map { it.key.str(env) }.asReversed()
        val variableValues = state.values.map { it.value.shortString() }.asReversed()

        val maxValueWidth = variableValues.map { it.length }.max() ?: 0
        val maxNameWidth = variableNames.map { it.length }.max() ?: 0

        val codeWidth = width - maxValueWidth - maxNameWidth - 2

        val lineCount = max(prgmLines.size, variableNames.size)
        val lines = (0 until lineCount).map { i ->
            val hasVariable = 0 <= i && i < variableNames.size

            (prgmLines.getOrNull(i) ?: "").ansiPadEnd(if (hasVariable) codeWidth else 0) +
            if (hasVariable) {
                (variableNames.getOrNull(i) ?: "").ansiPadEnd(maxNameWidth + 2) +
                (variableValues.getOrNull(i) ?: "").ansiPadEnd(maxValueWidth)
            } else ""
        }.asReversed()

        println("\n\n" + lines.joinToString("\n"))
    }

    private fun renderPrompt() {
        print("${ANSI_BLUE}dbg> $ANSI_RESET")
    }

    private suspend fun SequenceScope<String>.renderBlock(block: BasicBlock) {
        val color = if (block == state.currBlock) "" else ANSI_GRAY

        yield("$color${block.str(env)}${if (block == state.prevBlock) "$ANSI_BLUE ->" else ""}$ANSI_RESET")

        for (instr in block.instructions) {
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