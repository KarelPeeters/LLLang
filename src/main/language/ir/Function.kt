package language.ir

class Function private constructor(
        val name: String?,
        val parameters: List<ParameterValue>,
        val returnType: Type
) : Value(FunctionType(parameters.map(ParameterValue::type), returnType)) {
    var entry by operand<BasicBlock>(null)
    val blocks = mutableListOf<BasicBlock>()

    private var _program: Program? = null

    val program get() = _program!!
    fun setProgram(program: Program?) {
        this._program = program
    }

    companion object {
        operator fun invoke(name: String?, parameters: List<Pair<String?, Type>>, returnType: Type) =
                Function(name, parameters.map { ParameterValue(it.first, it.second) }, returnType)
    }

    /**
     * Create a new function with the same content but with a different signature.
     * As this creates a shallow copy, the current function is shallowDeleted.
     */
    fun changedSignature(parameters: List<ParameterValue>, returnType: Type): Function {
        val newFunc = Function(this.name, parameters, returnType)

        newFunc.addAll(this.blocks)
        newFunc.entry = this.entry
        newFunc._program = this._program

        this.delete()
        return newFunc
    }

    fun deepClone(): Function {
        val newFunc = Function(this.name, this.parameters.map { it.name to it.type }, this.returnType)
        val paramMap = this.parameters.zip(newFunc.parameters).toMap()

        //shallow clone, keep mappings old -> new
        val instrMap = mutableMapOf<Instruction, Instruction>()
        val blockMap = blocks.associateWith { oldBlock ->
            val newBlock = BasicBlock(oldBlock.name)
            for (instr in oldBlock.instructions) {
                val newInstr = instr.clone()
                instrMap[instr] = newInstr
                newBlock.appendOrReplaceTerminator(newInstr)
            }
            newBlock
        }

        //replace instructions and blocks
        val replaceMap = paramMap + instrMap + blockMap
        for (node in instrMap.values) {
            for ((old, new) in replaceMap)
                node.replaceOperand(old, new)
        }

        //finish new function
        for (block in blockMap.values)
            newFunc.add(block)
        newFunc.entry = blockMap.getValue(this.entry)
        return newFunc
    }

    fun add(block: BasicBlock) {
        this.blocks += block
        block.setFunction(this)
    }

    fun add(index: Int, block: BasicBlock) {
        this.blocks.add(index, block)
        block.setFunction(this)
    }

    fun addAll(blocks: List<BasicBlock>) {
        this.blocks.addAll(blocks)
        for (block in blocks)
            block.setFunction(this)
    }

    fun addAll(index: Int, blocks: List<BasicBlock>) {
        this.blocks.addAll(index, blocks)
        for (block in blocks)
            block.setFunction(this)
    }

    fun remove(block: BasicBlock) {
        require(this.blocks.remove(block))
        block.setFunction(null)
    }

    fun deepDelete() {
        for (block in blocks)
            block.deepDelete()
        delete()
    }

    override fun delete() {
        for (param in parameters) {
            param.delete()
        }
        super.delete()
    }

    fun entryAllocs() = entry.instructions.filterIsInstance<Alloc>()

    fun fullStr(env: NameEnv): String {
        blocks.forEach { env.block(it) } //preset names to keep them ordered

        val nameStr = env.function(this)
        val paramStr = parameters.joinToString { it.str(env, true) }
        val returnStr = if (returnType == UnitType) "" else ": $returnType"
        val entryStr = if (entry != blocks.first()) "\n  entry: ${entry.str(env, false)}" else ""

        return "fun %$nameStr($paramStr)$returnStr { $entryStr\n" +
               blocks.joinToString("\n") { it.fullStr(env) } + "\n}\n"
    }

    override fun untypedStr(env: NameEnv) = "%" + env.function(this)
}

class ParameterValue(val name: String?, type: Type) : Value(type) {
    override fun untypedStr(env: NameEnv) = "%${env.value(this)}"
}

data class FunctionType(val paramTypes: List<Type>, val returnType: Type) : Type() {
    override fun toString() = "(" + paramTypes.joinToString { it.toString() } + ") -> $returnType"
}
