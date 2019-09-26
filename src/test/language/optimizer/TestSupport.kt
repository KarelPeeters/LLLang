package language.optimizer

import language.ir.Function
import language.ir.Program
import language.ir.support.IrParser
import language.ir.support.Verifier

/**
 * Run a pass on a program, return whether the pass reported a change to the context.
 */
fun runPass(pass: OptimizerPass, program: Program): Boolean {
    val context = object : OptimizerContext {
        var changed = false
        override fun changed() {
            changed = true
        }

        override fun domInfo(function: Function) = DominatorInfo(function)
    }
    with(pass) { context.runOnProgram(program) { /* do nothing */ } }
    return context.changed
}

fun verifyWithMessage(program: Program, message: () -> String) {
    try {
        Verifier.verifyProgram(program)
    } catch (e: Exception) {
        throw IllegalStateException(message(), e)
    }
}

fun readFunction(fileName: String): Function {
    val string = ::readFunction.javaClass.getResource(fileName).readText()
    val program = IrParser.parse(string)

    check(program.functions.size == 1) { "program can only contain a single function" }
    return program.functions.first()
}
