package language.optimizer

import language.ir.Function
import language.ir.Program
import language.ir.ProgramNameEnv
import language.ir.support.IrParser
import language.ir.support.Verifier
import language.ir.support.programEquals
import org.apache.commons.io.IOUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.fail

fun testBeforeAfter(name: String, pass: OptimizerPass) {
    val (before, after) = readFile(name)

    verifyWithMessage(before) { "before invalid" }
    verifyWithMessage(after) { "after invalid" }

    val shouldChange = !programEquals(before, after)

    var changed = false
    val context = object : OptimizerContext {
        override fun changed() {
            changed = true
        }

        override fun domInfo(function: Function) = DominatorInfo(function)
    }

    with(pass) {
        context.runOnProgram(before) { }
    }

    verifyWithMessage(before) { "result of pass invalid" }

    if (!programEquals(before, after)) {
        assertEquals(after.fullString(ProgramNameEnv()), before.fullString(ProgramNameEnv())) { "After mismatch" }
        fail { "Programs don't equal but string representations do" }
    }

    if (shouldChange) {
        assert(changed) { "program changed but pass didn't report it" }
    } else {
        assert(!changed) { "program didn't change but pass reported it did" }
    }
}

private fun verifyWithMessage(program: Program, message: () -> String) {
    try {
        Verifier.verifyProgram(program)
    } catch (e: Exception) {
        throw IllegalStateException(message(), e)
    }
}

private fun readFile(name: String): Pair<Program, Program> {
    val string = resourceToString("passes/$name").trim()

    val (beforeString, afterString) = when {
        string.startsWith("//before") -> {
            val parts = string.split("//after")

            if (parts.size == 1) error("Missing '//after'")
            if (parts.size > 2) error("Multiple '//after'")

            parts[0] to parts[1]
        }
        string.startsWith("//unchanged") -> {
            val code = string.removePrefix("//unchanged")
            code to code
        }
        else -> error("unrecognized start '${string.substring(0, 10)}'")
    }

    val before = IrParser.parse(beforeString)
    val after = IrParser.parse(afterString)

    return before to after
}

private fun resourceToString(path: String): String {
    val res = ::resourceToString::class.java.getResourceAsStream(path) ?: error("'$path' not found")
    return IOUtils.toString(res, Charsets.UTF_8)
}
