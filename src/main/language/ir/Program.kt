package language.ir

import language.util.hasDuplicates

class Program : Node() {
    var entry by operand<Function>(null)
    val functions = mutableListOf<Function>()

    fun addFunction(function: Function) {
        functions.add(function)
        function.setProgram(this)
    }

    fun removeFunction(function: Function) {
        functions.remove(function)
        function.setProgram(null)
    }

    override fun doVerify() {
        check(entry in functions) { "entry must be one of the functions" }
        check(entry.parameters.isEmpty()) { "entry must be a parameterless function" }

        val blocks = functions.flatMap { it.blocks }
        val instructions = functions.flatMap { f -> f.blocks.flatMap { b -> b.instructions } }
        check(!blocks.hasDuplicates()) { "no duplicate blocks" }
        check(!instructions.hasDuplicates()) { "no duplicate instructions" }

        for (function in functions) {
            check(function.program == this) { "functions must refer to this program" }
            function.verify()
        }
    }

    fun fullString(prgmEnv: ProgramNameEnv): String {
        functions.forEach { prgmEnv.function(it) } //preset names to keep them ordered
        return functions.joinToString("\n\n") { it.fullStr(prgmEnv.subEnv(it)) }
    }
}