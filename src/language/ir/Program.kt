package language.ir

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

    override fun verify() {
        require(entry in functions) { "entry must be one of the functions" }
        require(entry.parameters.isEmpty()) { "entry must be a parameterless function" }

        functions.forEach {
            require(it.program == this)
            it.verify()
        }
    }

    fun fullString(prgmEnv: ProgramNameEnv): String {
        functions.forEach { prgmEnv.function(it) } //preset names to keep them ordered
        return functions.joinToString("\n\n") { it.fullStr(prgmEnv.subEnv(it)) }
    }
}