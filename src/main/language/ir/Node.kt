package language.ir

import language.ir.support.findInnerNodes
import language.ir.support.visitNodes

/**
 * The base class of all IR nodes.
 * A [Node] represents both a value and a user. As a user it has operands, as a value it has users.
 * [operands] contains the nodes used by this node as a user. Su
 */
sealed class Node(open val type: Type) : NodeImpl() {
    open val replaceAble get() = true

    var name: String? = null

    protected fun project(index: Int) = Project(this, index)

    open fun typeCheck() {}

    open fun untypedString(namer: (Node) -> String) = namer(this)

    fun typedString(namer: (Node) -> String) = this.untypedString(namer) + " $type"
}

interface Instruction {
    fun fullString(namer: (Node) -> String): String
}

/**
 * [Project] represents indexing into a tuple typed result. Instances of this class should only be created at
 * construction time by the [Node] that then passes itself as the [node] parameter.
 */
class Project(
        node: Node,
        val index: Int
) : Node((node.type as TupleType)[index]) {
    val node by operand(node, node.type)
}

class Program : Node(VoidType), Instruction {
    var entry: Function by operand(type = FunctionType(listOf(MemType), listOf(MemType)))

    override fun fullString(namer: (Node) -> String): String {
        val funcs = visitNodes(this.entry).filterIsInstance<Function>()
        return funcs.joinToString("\n\n") { it.fullString(namer) }
    }
}

class Function(
        override val type: FunctionType
) : Constant(type) {
    var entry: Region by operand()

    //memory values to keep used so their effects don't die off in endless loops
    val keepAlive by operandList<Node>(type = MemType)

    val parameters: List<Parameter> = type.parameters.map { Parameter(it) }

    override fun typeCheck() {
        check(keepAlive.all { it.type == MemType })
        check(findInnerNodes(this).filterIsInstance<Return>().all { it.types == type.returns })
    }

    fun fullString(namer: (Node) -> String): String {
        val params = parameters.joinToString { it.typedString(namer) }
        val retuns = type.returns.joinToString()
        val body = findInnerNodes(this)
                .filterIsInstance<Instruction>()
                .joinToString("\n") { "    " + it.fullString(namer) }

        return "fun ${untypedString(namer)}($params) -> $retuns {\n$body\n}"
    }
}

class Parameter(type: Type) : Node(type)

class Region : Node(RegionType), Instruction {
    var terminator: Terminator by operand()

    override val replaceAble get() = false

    override fun fullString(namer: (Node) -> String) =
            "${typedString(namer)} { ${terminator.fullString(namer)} }"
}

sealed class Terminator : Node(VoidType) {
    abstract fun fullString(namer: (Node) -> String): String
}

class Jump(
        target: Region
) : Terminator() {
    var target: Region by operand(target)

    override fun fullString(namer: (Node) -> String): String {
        val t = target.untypedString(namer)
        return "jump $t"
    }
}

class Branch(
        condition: Node,
        ifTrue: Region,
        ifFalse: Region
) : Terminator() {
    var condition by operand(condition, type = IntegerType.bool)
    var ifTrue: Region by operand(ifTrue)
    var ifFalse: Region by operand(ifFalse)

    override fun fullString(namer: (Node) -> String): String {
        val c = condition.typedString(namer)
        val t = ifTrue.untypedString(namer)
        val f = ifFalse.untypedString(namer)
        return "branch $c $t $f"
    }
}

class Return(
        values: List<Node>
) : Terminator() {
    val types = values.map { it.type }
    val values by operandList(values, types = types)

    override fun typeCheck() {
        check(types.size == values.size)
        check((types zip values).all { (t, v) -> v.type == t })
    }

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
    val values by operandValueMap<Region, Node>(type = type)

    override fun fullString(namer: (Node) -> String): String {
        val th = this.typedString(namer)
        val r = region.untypedString(namer)
        val v = values.toList().joinToString { (k, v) -> (k.untypedString(namer)) + ": " + v.untypedString(namer) }
        return "$th = phi $r [$v]"
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

class PlaceHolder(type: Type) : Node(type)
