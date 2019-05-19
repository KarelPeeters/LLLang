package language.interpreter

import language.interpreter.Color.*
import language.ir.BasicBlock
import language.ir.Function
import language.ir.Instruction
import language.ir.NameEnv
import language.ir.Program
import language.ir.ProgramNameEnv
import kotlin.math.max

private const val ANSI_RESET = "\u001B[0m"

private enum class Color(val ansi: String) {
    WHITE(""),
    RED("\u001B[31m"),
    GREEN("\u001B[32m"),
    BLUE("\u001B[34m"),
    GRAY("\u001B[37m");
}

private fun colored(str: String, color: Color) = color.ansi + str + ANSI_RESET
private fun red(str: String) = colored(str, RED)
private fun green(str: String) = colored(str, GREEN)
private fun blue(str: String) = colored(str, BLUE)
private fun gray(str: String) = colored(str, GRAY)

class Debugger(
        val program: Program,
        val prgmEnv: ProgramNameEnv = ProgramNameEnv()
) {
    private lateinit var interpreter: Interpreter

    private lateinit var state: State
    private val frame get() = state.topFrame

    private val breakPoints = mutableSetOf<Instruction>()

    private val out = StringBuilder()

    fun start() {
        reset()
        printInterface()

        main@ while (true) {
            printPrompt()

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
            "", "." -> {
                val depth = state.stack.size
                do step() while (!done() && state.stack.size > depth)
                printInterface()
            }
            "o" -> {
                val depth = state.stack.size
                do step() while (!done() && state.stack.size >= depth)
                printInterface()
            }
            "i" -> {
                step()
                printInterface()
            }
            "c" -> while (!done()) {
                step()
                if (atBreakPoint())
                    break
                printInterface()
            }
            "b" -> {
                state.topFrame.current.let { breakPoints.toggle(it) }
                printInterface()
            }
            "r" -> {
                reset()
                printInterface()
            }
            "f" -> out.appendln(renderFunction(frame.currFunction))
            "p" -> out.appendln(renderProgram(program))
            "q" -> return false
            else -> out.appendln(red("Unknown command '$cmd'"))
        }

        return true
    }

    private fun reset() {
        interpreter = Interpreter(program)
        state = interpreter.step()
    }

    private fun step() {
        if (done())
            return
        state = interpreter.step()
    }

    private fun atBreakPoint() = state.topFrame.current in breakPoints

    private fun done() = interpreter.isDone()

    private fun printInterface() {
        out.append("\n\n\n\n\n\n")
        val env = prgmEnv.subEnv(frame.currFunction)

        val codeLines = renderCode(env)
        val varLines = renderVariables(env)
        val stackLines = renderStack(env)

        val codeWidth = 70 //max(70, (codeLines.maxBy { it.length }?.length ?: 0) + 2)
        val varWidth = 15 //max(15, (varLines.maxBy { it.length }?.length ?: 0) + 2)

        val lineCount = max(codeLines.size, varLines.size, stackLines.size)

        val result = (-lineCount until 0).joinToString("\n") { i ->
            val codeLine = codeLines.getOrElse(codeLines.size + i) { "" }
            val varLine = varLines.getOrElse(varLines.size + i) { "" }
            val stackLine = stackLines.getOrElse(stackLines.size + i) { "" }

            codeLine.ansiPadEnd(codeWidth) + varLine.ansiPadEnd(varWidth) + stackLine
        }
        out.appendln(result)
    }

    private fun printPrompt() {
        out.append(blue("dbg>") + " ")
        print(out.toString())
        out.clear()
    }

    private fun renderCode(env: NameEnv): List<String> = sequence<String> {
        val frame = frame
        if (frame.prevBlock != null)
            renderBlock(frame.prevBlock, env)
        renderBlock(frame.currBlock, env)
        for (succ in frame.currBlock.successors())
            renderBlock(succ, env)
    }.toList()

    private fun renderVariables(env: NameEnv): List<String> {
        val names = frame.values.map { (k, _) -> k.str(env) }
        val values = frame.values.map { (_, v) -> v.shortString() }

        val maxNameWidth = names.maxBy { it.length }?.length ?: 0
        return names.zip(values) { n, v ->
            n.padEnd(maxNameWidth + 1) + v
        }
    }

    private fun renderStack(env: NameEnv): List<String> = state.stack.map {
        val func = it.currFunction.str(env)
        val block = it.currBlock.str(env)
        val instr = it.current.indexInBlock()
        "$func:$block:$instr"
    }

    private fun renderProgram(program: Program): String =
            program.functions.joinToString("\n") {
                renderFunction(it)
            }

    private fun renderFunction(function: Function): String = buildString {
        with(function) {
            val env = prgmEnv.subEnv(function)
            val color = if (function == frame.currFunction) WHITE else GRAY

            val paramStr = parameters.joinToString { it.str(env) }
            appendln(colored("fun $name($paramStr): $returnType {", color))
            appendln(colored("  entry: ${entry.str(env)}", color))
            for (block in blocks) {
                for (line in sequence<String> { renderBlock(block, env) })
                    appendln("  $line")
            }
        }
    }

    private suspend fun SequenceScope<String>.renderBlock(block: BasicBlock, env: NameEnv) {
        val color = if (block == frame.currBlock) WHITE else GRAY

        val postHeader = if (block == frame.prevBlock && block in frame.currBlock.successors())
            " ↔" else "  "

        val header = block.str(env)

        yield(colored(header + postHeader, color))

        for (instr in block.instructions) {
            val pointer = if (instr == frame.current) ">" else " "
            val pointerColor = if (done()) GRAY else GREEN
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
