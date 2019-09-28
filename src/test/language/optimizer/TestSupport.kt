package language.optimizer

import language.ir.Function
import language.ir.Program
import language.ir.support.IrParser
import language.ir.support.Verifier
import kotlin.reflect.KClass

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

fun KClass<*>.readFunction(fileName: String): Function {
    val program = this.readProgram(fileName)
    return program.functions.single()
}

fun KClass<*>.readProgram(fileName: String): Program {
    val string = this.java.getResource(fileName).readText()
    return IrParser.parse(string)
}