package language.interpreter

import language.ir.BasicBlock
import language.ir.Instruction
import language.ir.NameEnv
import language.ir.Program
import kotlin.math.max

const val ANSI_RESET = "\u001B[0m"
const val ANSI_RED = "\u001B[31m"
const val ANSI_GREEN = "\u001B[32m"
const val ANSI_BLUE = "\u001B[34m"
const val ANSI_GRAY = "\u001B[37m"

private fun colored(str: String, color: String) = color + str + ANSI_RESET
private fun red(str: String) = colored(str, ANSI_RED)
private fun green(str: String) = colored(str, ANSI_GREEN)
private fun blue(str: String) = colored(str, ANSI_BLUE)
private fun gray(str: String) = colored(str, ANSI_GRAY)

val WIDTH_REGEX = """w ([+-]?)(\d+)""".toRegex()

class Debugger(val program: Program, val env: NameEnv) {
    private val interpreter = Interpreter(program)
    private val breakPoints = mutableSetOf<Instruction>()

    private var state = interpreter.step()
    private var frame = state.topFrame

    fun start() {
        render()

        main@ while (true) {
            val line = readLine() ?: break@main
            if (line == "") {
                if (!doCommand("")) break@main
            } else {
                for (c in line) {
                    val cont = doCommand(c.toString())
                    if (!cont) break@main
                }
            }
        }
    }

    private fun doCommand(cmd: String): Boolean {
        when (cmd) {
            "" -> {
                val depth = state.stack.size
                do step() while (!done() && state.stack.size > depth)
            }
            "o" -> {
                val depth = state.stack.size
                do step() while (!done() && state.stack.size >= depth)
            }
            "i" -> step()
            "c" -> while (!done()) {
                step()
                if (atBreakPoint())
                    break
            }
            "b" -> state.topFrame.current?.let { breakPoints.toggle(it) }
            "q" -> return false
            else -> {
                println(red("Unknown command '$cmd'"))
                return true //skip render
            }
        }

        render()
        return true
    }

    private fun step() {
        if (done())
            return

        state = interpreter.step()
        frame = state.topFrame
    }

    private fun atBreakPoint() = state.topFrame.current in breakPoints

    private fun done() = interpreter.isDone()

    private fun render() {
        val codeLines = renderCode()
        val varLines = renderVariables()
        val stackLines = renderStack()

        val codeWidth = 70 //max(70, (codeLines.maxBy { it.length }?.length ?: 0) + 2)
        val varWidth = 15 //max(15, (varLines.maxBy { it.length }?.length ?: 0) + 2)

        val lineCount = max(codeLines.size, varLines.size, stackLines.size)

        val result = (-lineCount until 0).joinToString(
                "\n",
                postfix = "\n" + blue("dbg> ")
        ) { i ->
            val codeLine = codeLines.getOrElse(codeLines.size + i) { "" }
            val varLine = varLines.getOrElse(varLines.size + i) { "" }
            val stackLine = stackLines.getOrElse(stackLines.size + i) { "" }

            codeLine.ansiPadEnd(codeWidth) + varLine.ansiPadEnd(varWidth) + stackLine
        }
        print(result)
    }

    private fun renderCode(): List<String> = sequence<String> {
        val frame = frame
        if (frame.prevBlock != null) renderBlock(frame.prevBlock)
        if (frame.currBlock != null) {
            renderBlock(frame.currBlock)
            for (succ in frame.currBlock.successors())
                renderBlock(succ)
        }
    }.toList()

    private fun renderVariables(): List<String> {
        val names = frame.values.map { (k, _) -> k.str(env) }
        val values = frame.values.map { (_, v) -> v.shortString() }

        val maxNameWidth = names.maxBy { it.length }?.length ?: 0
        return names.zip(values) { n, v ->
            n.padEnd(maxNameWidth + 1) + v
        }
    }

    private fun renderStack(): List<String> = state.stack.map {
        val func = it.currFunction?.str(env) ?: "null"
        val block = it.currBlock?.str(env) ?: "null"
        val instr = it.currBlock?.instructions?.indexOf(it.current)
        "$func:$block:$instr"
    }

    private suspend fun SequenceScope<String>.renderBlock(block: BasicBlock) {
        val color = if (block == frame.currBlock) "" else ANSI_GRAY

        val postHeader = if (block == frame.prevBlock && block in frame.currBlock?.successors() ?: emptySet())
            " ↔" else "  "

        val header = block.str(env)

        yield(colored(header + postHeader, color))

        for (instr in block.instructions) {
            val pointer = if (instr == frame.current) ">" else " "
            val pointerColor = if (done()) ANSI_GRAY else ANSI_GREEN
            val breakPoint = if (instr in breakPoints) "*" else " "
            val code = instr.fullStr(env)

            yield("  ${colored(pointer, pointerColor)}${red(breakPoint)} ${colored(code, color)}")
        }
    }
}

private fun max(a: Int, b: Int, c: Int) = max(a, max(b, c))

private fun String.ansiPadEnd(length: Int, padChar: Char = ' '): String {
    val currentLength = this.replace("\u001B\\[[;\\d]*m".toRegex(), "").length
    return this + padChar.toString().repeat(max(length - currentLength, 0))
}

private fun <E> MutableSet<in E>.toggle(element: E) {
    if (element in this) this -= element
    else this += element
}
