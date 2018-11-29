package language.ir

class Program : Value(UnitType) {
    var entry by operand<Function>(null)
    val functions = mutableListOf<Function>()

    override fun verify() {
        require(entry in functions) { "entry must be one of the functions" }
        require(entry.parameters.isEmpty()) { "entry must be a parameterless function" }

        functions.forEach { it.verify() }
    }

    fun fullString() = functions.joinToString("\n\n") { it.fullStr(NameEnv()) }

    override fun str(env: NameEnv) = toString()
}