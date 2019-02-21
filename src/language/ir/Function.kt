package language.ir

class Function(
        val name: String,
        parameters: List<Pair<String?, Type>>,
        val returnType: Type
) : Value(FunctionType(parameters.map { it.second }, returnType)) {
    var entry by operand<BasicBlock>(null)
    val parameters = parameters.map { (name, type) -> ParameterValue(name, type) }
    val blocks = mutableListOf<BasicBlock>()

    private var _program: Program? = null
    val program get() = _program!!

    fun setProgram(program: Program?) {
        this._program = program
    }

    fun deepClone(): Function {
        val newFunc = Function(this.name, this.parameters.map { it.name to it.type }, this.returnType)
        val paramMap = this.parameters.zip(newFunc.parameters).toMap()

        //shallow clone, keep mappings old -> new
        val instrMap = mutableMapOf<Instruction, Instruction>()
        val blockMap = blocks.associate { oldBlock ->
            val newBlock = BasicBlock(oldBlock.name)
            for (instr in oldBlock.instructions) {
                val newInstr = instr.clone()
                instrMap[instr] = newInstr
                newBlock.appendOrReplaceTerminator(newInstr)
            }
            oldBlock to newBlock
        }

        //replace instructions and blocks
        val replaceMap = paramMap + instrMap + blockMap
        for (node in instrMap.values + blockMap.values) {
            for ((old, new) in replaceMap)
                node.replaceOperand(old, new)
        }

        //finish new function
        for (block in blockMap.values)
            newFunc.add(block)
        newFunc.entry = blockMap.getValue(this.entry)
        return newFunc
    }

    override fun verify() {
        check(entry in blocks) { "entry must be one of the blocks" }

        for (block in blocks) {
            val term = block.terminator
            if (term is Return)
                check(term.value.type == returnType) { "return type must match, ${term.value.type} != $returnType" }

            check(block.function == this) { "blocks must refer to this function" }
            block.verify()
        }
    }

    fun add(block: BasicBlock) {
        this.blocks += block
        block.setFunction(this)
    }

    fun add(index: Int, block: BasicBlock) {
        this.blocks.add(index, block)
        block.setFunction(this)
    }

    fun remove(block: BasicBlock) {
        require(this.blocks.remove(block))
        block.setFunction(null)
    }

    fun deepDelete() {
        for (block in blocks)
            block.deepDelete()
        shallowDelete()
    }

    fun fullStr(env: NameEnv): String {
        blocks.forEach { env.block(it) } //preset names to keep them ordered

        val nameStr = env.function(this)
        val paramStr = parameters.joinToString { it.str(env) }
        val returnStr = if (returnType == UnitType) "" else ": $returnType"

        return """
            fun $nameStr($paramStr): $returnStr {
                entry: ${entry.str(env)}

        """.trimIndent() + blocks.joinToString("\n\n    ", prefix = "    ") { it.fullStr(env).replace("\n", "\n    ") } + "\n}\n"
    }

    override fun str(env: NameEnv) = env.function(this)
}

class ParameterValue(val name: String?, type: Type) : Value(type) {
    override fun verify() {}
    override fun str(env: NameEnv) = "%${env.value(this)} $type"
}

data class FunctionType(val paramTypes: List<Type>, val returnType: Type) : Type() {
    override fun toString() = "(" + paramTypes.joinToString { it.toString() } + ") -> $returnType"
}
