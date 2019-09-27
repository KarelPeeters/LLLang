package language.optimizer

import language.ir.Program
import language.ir.ProgramNameEnv
import language.ir.support.IrParser
import language.ir.support.programEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.fail

fun testBeforeAfter(name: String, pass: OptimizerPass) {
    val (input, expected) = readBeforeAfterPrograms("passes/$name")

    verifyWithMessage(input) { "input invalid" }
    verifyWithMessage(expected) { "expected invalid" }

    val shouldChange = !programEquals(input, expected)
    val beforeValues = programUsers(input)

    val changed = runPass(pass, input)

    val afterValues = programUsers(input)

    verifyWithMessage(input) { "result of pass invalid" }

    val equals = programEquals(input, expected)
    val flippedEquals = programEquals(expected, input)
    assertEquals(equals, flippedEquals) { "programEquals is not reflexive" }

    //check input/expected match
    if (!equals) {
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

private fun readBeforeAfterPrograms(fileName: String): Pair<Program, Program> {
    val string = ::readBeforeAfterPrograms.javaClass.getResource(fileName).readText().trim()

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