package language.ir

class Function(val name: String, parameters: List<Pair<String?, Type>>, val returnType: Type) : Value(FunctionType) {
    var entry by operand<BasicBlock>(null)
    val parameters = parameters.map { ParameterValue(it.first, it.second) }
    val blocks = mutableListOf<BasicBlock>()

    init {
        for (param in this.parameters) {
            param.users += this
        }
    }

    override fun verify() {
        require(entry in blocks) { "entry must be one of the blocks" }
        require(blocks.all { it.function == this }) { "block.function must be this function" }

        blocks.forEach { it.verify() }
    }

    fun append(block: BasicBlock) {
        this.blocks += block
        block.setFunction(this)
    }

    fun remove(block: BasicBlock) {
        require(this.blocks.remove(block))
        block.setFunction(null)
    }

    fun fullStr(env: NameEnv): String {
        blocks.forEach { env.block(it) } //preset names to keep them ordered

        return """
            fun $name(${parameters.joinToString { it.str(env) }}): $returnType {
            entry: ${entry.str(env)}

        """.trimIndent() + blocks.joinToString("\n\n") { it.fullStr(env) } + "\n}\n"
    }

    override fun str(env: NameEnv) = name
}

class ParameterValue(val name: String?, type: Type) : Value(type) {
    override fun verify() {}
    override fun str(env: NameEnv) = "%${env.value(this)} $type"
}

object FunctionType : Type()