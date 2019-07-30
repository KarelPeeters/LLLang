package language.ir

class Function private constructor(
        val name: String?,
        val parameters: List<ParameterValue>,
        val returnType: Type,
        attributes: Set<Attribute>
) : Value(FunctionType(parameters.map(ParameterValue::type), returnType)) {
    var entry by operand<BasicBlock>(null)
    val blocks = mutableListOf<BasicBlock>()
    val attributes = attributes.toMutableSet()

    private var _program: Program? = null

    val program get() = _program!!
    fun setProgram(program: Program?) {
        this._program = program
    }

    companion object {
        operator fun invoke(name: String?, parameters: List<Pair<String?, Type>>, returnType: Type, attributes: Set<Attribute>) =
                Function(name, parameters.map { ParameterValue(it.first, it.second) }, returnType, attributes)
    }

    /**
     * Create a new function with the same content but with a different signature.
     * As this creates a shallow copy, the current function is shallowDeleted.
     */
    fun changedSignature(parameters: List<ParameterValue>, returnType: Type): Function {
        val newFunc = Function(this.name, parameters, returnType, attributes)

        newFunc.addAll(this.blocks)
        newFunc.entry = this.entry
        newFunc._program = this._program

        for (param in this.parameters) {
            if (param !in parameters)
                param.delete()
        }
        this.delete(deleteParameters = false)

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

    fun delete(deleteParameters: Boolean) {
        if (deleteParameters) {
            for (param in parameters)
                param.delete()
        }
        super.delete()
    }

    override fun delete() = delete(true)

    fun isEntryFunction() = program.entry == this

    fun entryAllocs() = entry.instructions.filterIsInstance<Alloc>()

    fun fullStr(env: NameEnv): String {
        blocks.forEach { env.block(it) } //preset names to keep them ordered

        val attrString = if (attributes.isNotEmpty()) attributes.joinToString("\n", postfix = "\n") { "@${it.name}" } else ""
        val nameStr = env.function(this)
        val paramStr = parameters.joinToString { it.str(env, true) }
        val returnStr = if (returnType == VoidType) "" else ": $returnType"
        val entryStr = if (entry != blocks.first()) "\n  entry: ${entry.str(env, false)}" else ""

        return "${attrString}fun %$nameStr($paramStr)$returnStr { $entryStr\n" +
               blocks.joinToString("\n") { it.fullStr(env) } + "\n}\n"
    }

    override fun untypedStr(env: NameEnv) = "%" + env.function(this)

    enum class Attribute {
        NoInline,
        ;

        companion object {
            fun findByName(name: String) = values().find { it.name == name }
        }
    }
}

class ParameterValue(val name: String?, type: Type) : Value(type) {
    override fun untypedStr(env: NameEnv) = "%${env.value(this)}"
}

data class FunctionType(val paramTypes: List<Type>, val returnType: Type) : Type() {
    override fun toString() = "(" + paramTypes.joinToString { it.toString() } + ") -> $returnType"
}
