package language.ir

import language.ir.support.BasicSchedule
import language.ir.support.Visitor

/**
 * The base class of all IR nodes.
 * A [Node] represents both a value and a user. As a user it has operands, as a value it has users.
 * [operands] contains the nodes used by this node as a user. Su
 */
sealed class Node(open val type: Type) : NodeImpl() {
    open val replaceAble get() = true

    var name: String? = null

    open fun untypedString(namer: (Node) -> String) = namer(this)

    fun typedString(namer: (Node) -> String) = this.untypedString(namer) + " $type"
}

/**
 * A [Node] that has an instruction-like string representation.
 * Note that this interface does not mean anything for the semantics of the node.
 */
interface Instruction {
    fun fullString(namer: (Node) -> String): String
}

class Program : Node(VoidType) {
    var end: Function by operand(type = FunctionType(listOf(MemType), listOf(MemType)))

    fun fullString(namer: (Node) -> String): String {
        val functions = Visitor.findFunctions(this)
        return functions.joinToString("\n\n") { it.fullString(namer) }
    }
}

class Function(
        override val type: FunctionType
) : Constant(type) {
    val start = StartControl()
    val parameters: List<Parameter> = type.parameters.map { Parameter(it) }

    var ret: Return? by optionalOperand()

    val keepAliveMems by operandList<Node>(type = MemType)
    val keepAliveRegions by operandList<Region>()

    fun endRegions(): Collection<Region> =
            listOfNotNull(ret?.from) + keepAliveRegions

    fun fullString(namer: (Node) -> String): String {
        val params = parameters.joinToString { it.typedString(namer) }
        val returns = type.returns.joinToString()
        val body = BasicSchedule.build((this)).asIterable().joinToString("\n\n") { (region, instructions) ->
            (listOf(region.fullString(namer)) + instructions.map { it.fullString(namer) })
                    .joinToString("\n") { "    $it" }
        }

        return "fun ${untypedString(namer)}($params) -> $returns {\n$body\n}"
    }
}

class Parameter(type: Type) : Node(type)

class StartControl : ControlNode() {
    override val from: Region? get() = null

    override fun untypedString(namer: (Node) -> String) = "start"
}

interface Terminator : Instruction {
    val from: Region

}

sealed class ControlNode : Node(ControlType) {
    abstract val from: Region?

}

class Region : Node(RegionType) {
    val predecessors by operandList<ControlNode>()

    override val replaceAble get() = false

    fun fullString(namer: (Node) -> String): String {
        val th = typedString(namer)
        val pr = predecessors.joinToString { it.typedString(namer) }

        return "$th <- [$pr]"
    }
}

class Jump(
        from: Region
) : ControlNode(), Terminator {
    override var from: Region by operand(from)

    override fun fullString(namer: (Node) -> String): String {
        val th = this.typedString(namer)
        return "jump -> $th"
    }
}

class Branch(
        condition: Node,
        from: Region
) : Node(VoidType), Terminator {
    var condition by operand(condition, type = IntegerType.bool)
    override var from: Region by operand(from)

    var controlTrue = BranchControl(this, true)
    var controlFalse = BranchControl(this, false)

    override fun fullString(namer: (Node) -> String): String {
        val t = controlTrue.typedString(namer)
        val f = controlFalse.typedString(namer)
        val c = condition.typedString(namer)
        return "branch $c -> $t $f"
    }
}

class BranchControl(branch: Branch, val cond: Boolean) : ControlNode() {
    val branch: Branch by operand(branch)

    override val from: Region? get() = branch.from
}

class Return(
        from: Region,
        values: List<Node>
) : Node(VoidType), Terminator {
    val types = values.map { it.type }

    override var from: Region by operand(from)
    val values by operandList(values, types = types)

    override fun fullString(namer: (Node) -> String): String {
        val v = values.joinToString { it.typedString(namer) }
        return "return $v"
    }
}

class Phi(
        type: Type,
        region: Region
) : Node(type), Instruction {
    var region: Region by operand(region)
    val values by operandList<Node>(type = type)

    fun zippedValues(): Map<ControlNode, Node> {
        check(values.size == region.predecessors.size) { "phi values has different size than region predecessors" }
        val map = region.predecessors.withIndex().associateBy({ it.value }, { values[it.index] })
        check(map.size == values.size) { "region predecessors aren't distinct" }
        return map
    }

    override fun fullString(namer: (Node) -> String): String {
        val th = this.typedString(namer)
        val v = values.joinToString { it.untypedString(namer) }
        return "$th = phi [$v]"
    }
}

class Eat(
        beforeMem: Node,
        value: Node
) : Node(MemType), Instruction {
    var beforeMem by operand(beforeMem, type = MemType)
    var value by operand(value)

    override fun fullString(namer: (Node) -> String): String {
        val th = this.typedString(namer)
        val bm = beforeMem.typedString(namer)
        val v = value.typedString(namer)
        return "$th = eat $bm $v"
    }
}

class Blur(
        value: Node,
        val transparent: Boolean
) : Node(value.type), Instruction {
    var value by operand(value, this.type)

    override fun fullString(namer: (Node) -> String): String {
        val th = this.typedString(namer)
        val v = value.typedString(namer)
        val blur = if (transparent) "(blur)" else "blur"
        return "$th = $blur $v"
    }
}

class Alloc(
        beforeMem: Node,
        val innerType: Type
) : Node(VoidType), Instruction {
    var beforeMem by operand(beforeMem, type = MemType)

    val afterMem = AllocAfterMem(this)
    val result = AllocResult(this)

    override fun fullString(namer: (Node) -> String): String {
        val bm = beforeMem.typedString(namer)
        val am = afterMem.typedString(namer)
        val r = result.typedString(namer)
        return "$r, $am = alloc $innerType $bm"
    }
}

class AllocAfterMem(alloc: Alloc) : Node(MemType) {
    val alloc: Alloc by operand(alloc)
}

class AllocResult(alloc: Alloc) : Node(alloc.innerType.pointer) {
    val alloc: Alloc by operand(alloc)
}

class Load(
        beforeMem: Node,
        address: Node
) : Node(VoidType), Instruction {
    val resultType = address.type.unpoint!!

    var beforeMem by operand(beforeMem, type = MemType)
    var address by operand(address, type = address.type)

    val afterMem = LoadAfterMem(this)
    val result = LoadResult(this)

    override fun fullString(namer: (Node) -> String): String {
        val bm = beforeMem.typedString(namer)
        val am = afterMem.typedString(namer)
        val a = address.typedString(namer)
        val v = result.typedString(namer)
        return "$v, $am = load $a $bm"
    }
}

class LoadAfterMem(load: Load) : Node(MemType) {
    val load: Load by operand(load)
}

class LoadResult(load: Load) : Node(load.resultType) {
    val load: Load by operand(load)
}

class Store(
        beforeMem: Node,
        address: Node,
        value: Node
) : Node(MemType), Instruction {
    var beforeMem by operand(beforeMem, type = MemType)
    var address by operand(address, type = value.type.pointer)
    var value by operand(value, type = value.type)

    override fun fullString(namer: (Node) -> String): String {
        val th = this.typedString(namer)
        val bm = beforeMem.typedString(namer)
        val a = address.typedString(namer)
        val v = value.typedString(namer)
        return "$th = store $a $v $bm"
    }
}

class BinaryOp(
        val opType: BinaryOpType,
        left: Node,
        right: Node
) : Node(opType.returnType(left.type, right.type)), Instruction {
    var left by operand(left, left.type)
    var right by operand(right, right.type)

    override fun fullString(namer: (Node) -> String): String {
        val l = left.typedString(namer)
        val r = right.typedString(namer)
        val th = this.typedString(namer)
        return "$th = $opType $l $r"
    }
}

class Call(
        target: Node,
        arguments: List<Node>
) : Node(TupleType((target.type as FunctionType).returns)), Instruction {
    val funcType = target.type as FunctionType

    var target by operand(target, type = funcType)
    val arguments by operandList(arguments, types = funcType.parameters)

    val returns = funcType.returns.indices.map { CallResult(this, it) }

    override fun fullString(namer: (Node) -> String): String {
        val t = target.untypedString(namer)
        val a = arguments.joinToString { it.typedString(namer) }
        val r = returns.joinToString { it.typedString(namer) }

        return "$r = $t($a)"
    }
}

class CallResult(call: Call, val index: Int) : Node(call.funcType.returns[index]) {
    val call: Call by operand(call)
}

sealed class Constant(type: Type) : Node(type)

class IntegerConstant(
        override val type: IntegerType,
        val value: Int
) : Constant(type) {
    override val replaceAble get() = false

    override fun untypedString(namer: (Node) -> String) = "$value"
}

class Undef(type: Type) : Node(type) {
    override val replaceAble get() = false

    override fun untypedString(namer: (Node) -> String) = "undef"
}

class PlaceHolder(type: Type) : Node(type) {
    override fun untypedString(namer: (Node) -> String) = "PH(${namer(this)})"
}
