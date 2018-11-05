package language.ir

class NameEnv {
    private val values = SubEnv()
    private val blocks = SubEnv()

    fun value(node: Instruction) = values.name(node, node.name)
    fun block(node: BasicBlock) = blocks.name(node, node.name)

    private class SubEnv {
        private val nodeNames = mutableMapOf<Pair<Node, String?>, String>()
        private val nextIndex = mutableMapOf<String?, Int>()

        fun name(node: Node, name: String?): String {
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
}
