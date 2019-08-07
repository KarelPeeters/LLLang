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
    val (input, expected) = readFile(name)

    verifyWithMessage(input) { "input invalid" }
    verifyWithMessage(expected) { "expected invalid" }

    val shouldChange = !programEquals(input, expected)
    val beforeValues = programUsers(input)

    val changed = runPass(pass, input)

    val afterValues = programUsers(input)

    verifyWithMessage(input) { "result of pass invalid" }

    //check input/expected match
    if (!programEquals(input, expected)) {
        assertEquals(expected.fullString(ProgramNameEnv()), input.fullString(ProgramNameEnv())) { "result mismatch" }
        fail { "Programs don't equal but string representations do" }
    }

    //check change reporting
    if (shouldChange) {
        assert(changed) { "program changed but pass didn't report it" }
    } else {
        assert(!changed) { "program didn't change but pass reported it did" }
    }

    //check deletion of removed users
    for (removed in beforeValues - afterValues) {
        assert(removed.isDeleted) { "$removed was removed from program but not deleted" }
    }
}

private fun programUsers(program: Program) = sequence {
    yield(program)
    for (func in program.functions) {
        yield(func)
        yieldAll(func.parameters)
        for (block in func.blocks) {
            yield(block)
            yieldAll(block.instructions)
        }
    }
}.toSet()

private fun runPass(pass: OptimizerPass, program: Program): Boolean {
    var changed = false
    val context = object : OptimizerContext {
        override fun changed() {
            changed = true
        }

        override fun domInfo(function: Function) = DominatorInfo(function)
    }

    with(pass) {
        context.runOnProgram(program) { }
    }
    return changed
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
            val rest = string.removePrefix("//before")

            check("//before" !in rest) { "Multiple ´//before´" }
            check("//unchanged" !in rest) { "Unexpected ´//unchanged´ after ´//before´" }

            val parts = rest.split("//after")

            if (parts.size == 1) error("Missing '//after'")
            if (parts.size > 2) error("Multiple '//after'")

            parts[0] to parts[1]
        }
        string.startsWith("//unchanged") -> {
            val rest = string.removePrefix("//unchanged")

            check("//unchanged" !in rest) { "Multiple ´//unchanged´" }
            check("//before" !in rest) { "Unexpected ´//before´ after ´//unchanged´" }
            check("//after" !in rest) { "Unexpected ´//after´ after ´//unchanged´" }

            rest to rest
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
