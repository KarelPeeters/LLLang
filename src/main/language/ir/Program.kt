package language.ir

class Program : User() {
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

    fun fullString(prgmEnv: ProgramNameEnv): String {
        //type names
        for (type in findStructTypesInProgram(this))
            println("type $type = ${type.fullString()}")
        println()

        //code
        functions.forEach { prgmEnv.function(it) } //preset names to keep them ordered
        return functions.joinToString("\n\n") { it.fullStr(prgmEnv.subEnv(it)) }
    }
}

private fun findStructTypesInProgram(program: Program) = sequence {
    for (func in program.functions) {
        yield(func.returnType)
        yieldAll(func.parameters.asSequence().map { it.type })

        for (block in func.blocks) {
            for (instr in block.instructions) {
                yield(instr.type)
                yieldAll(instr.operands.asSequence().map { it.type })
            }
        }
    }
}.map { findUnpointedType(it) }.filterIsInstance<StructType>().toSet()

private fun findUnpointedType(type: Type): Type {
    var curr = type
    while (curr is PointerType)
        curr = curr.inner
    return curr
}