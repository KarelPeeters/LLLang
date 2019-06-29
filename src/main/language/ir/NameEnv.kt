package language.ir

class ProgramNameEnv {
    private val functions = PartEnv()
    private val subEnvs = mutableMapOf<Function, NameEnv>()

    fun function(node: Function) = functions.name(node, node.name)
    fun subEnv(function: Function) = subEnvs.getOrPut(function) { NameEnv(this) }
}

class NameEnv(val parent: ProgramNameEnv) {
    private val values = PartEnv()
    private val blocks = PartEnv()

    fun value(node: Instruction) = values.name(node, node.name)
    fun value(node: ParameterValue) = values.name(node, node.name)
    fun block(node: BasicBlock) = blocks.name(node, node.name)
    fun function(node: Function) = parent.function(node)
}

private class PartEnv {
    private val nodeNames = mutableMapOf<Pair<User, String?>, String>()
    private val nextIndex = mutableMapOf<String?, Int>()

    fun name(node: User, name: String?): String {
        name?.let { require(it.isNotEmpty()) { "names can't be empty, use null instead" } }

        val key = node to name
        nodeNames[key]?.let { return it }

        val index = nextIndex[name] ?: 0
        nextIndex[name] = index + 1

        val result = if (name != null && index == 0) name else (name ?: "") + index
        nodeNames[key] = result
        return result
    }
}
