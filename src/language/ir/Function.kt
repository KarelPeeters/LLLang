package language.ir

class Function(val name: String, parameters: List<Pair<String?, Type>>, val returnType: Type) : Value(FunctionType) {
    var entry by operand<BasicBlock>(null)
    val parameters = parameters.map { (name, type) -> ParameterValue(name, type) }
    val blocks = mutableListOf<BasicBlock>()

    init {
        for (param in this.parameters) {
            param.users += this
        }
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

                if (newInstr is Terminator)
                    newBlock.terminator = newInstr
                else
                    newBlock.append(newInstr)
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
            newFunc.append(block)
        newFunc.entry = blockMap.getValue(this.entry)
        return newFunc
    }

    override fun verify() {
        require(entry in blocks) { "entry must be one of the blocks" }
        require(blocks.all { it.function == this }) { "block.function must be this function" }
        for (block in blocks) {
            val term = block.terminator
            if (term is Return)
                require(term.value.type == returnType) { "return type must match, ${term.value.type} != $returnType" }
        }

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

    override fun delete() {
        super.delete()
        for (block in blocks) {
            block.delete(true)
        }
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

object FunctionType : Type()
