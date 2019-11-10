package language.ir

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
    val node by operand(node)
}

class Program : Node(VoidType), Instruction {
    var entry: Function by operand()

    override fun fullString(namer: (Node) -> String): String {
        val funcs = visitNodes(this.entry).filterIsInstance<Function>()
        return funcs.joinToString("\n\n") { it.fullString(namer) }
    }
}

class Function(
        override val type: FunctionType
) : Constant(type) {
    var entry: Region by operand()

    val parameters: List<Parameter> = type.parameters.map { Parameter(it) }

    fun fullString(namer: (Node) -> String): String {
        val params = parameters.joinToString { it.typedString(namer) }
        val retuns = type.returns.joinToString()
        val body = visitNodes(this.entry) { it !is Function }
                .filterIsInstance<Instruction>()
                .joinToString("\n") { "    " + it.fullString(namer) }

        return "fun ${untypedString(namer)}($params) -> $retuns {\n$body\n}"
    }
}

class Parameter(type: Type) : Node(type)

class Region : Node(RegionType), Instruction {
    var terminator by operand<Terminator>()

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
    var condition by operand(condition)
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
    val values by operandList(values)

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
    val values: MutableMap<Region, Node> by operandValueMap()

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
    var beforeMem by operand(beforeMem)
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
    var value by operand(value)

    override fun fullString(namer: (Node) -> String): String {
        val th = this.typedString(namer)
        val v = value.typedString(namer)
        return "$th = blur $v"
    }
}

class Alloc(
        beforeMem: Node,
        val inner: Type
) : Node(TupleType(MemType, inner.pointer)), Instruction {
    var beforeMem by operand(beforeMem)

    val afterMem = project(0)
    val result = project(1)

    override fun fullString(namer: (Node) -> String): String {
        val bm = beforeMem.typedString(namer)
        val am = afterMem.typedString(namer)
        val r = result.typedString(namer)
        return "$r, $am = alloc $inner $bm"
    }
}

class GetSubPointer(

)

class Load(
        beforeMem: Node,
        address: Node
) : Node(TupleType(MemType, address.type.unpoint!!)), Instruction {
    var beforeMem by operand(beforeMem)
    var address by operand(address)

    val afterMem = project(0)
    val value = project(1)

    override fun fullString(namer: (Node) -> String): String {
        val bm = beforeMem.typedString(namer)
        val am = afterMem.typedString(namer)
        val a = address.typedString(namer)
        val v = value.typedString(namer)
        return "$v, $am = load $a $bm"
    }
}

class Store(
        beforeMem: Node,
        address: Node,
        value: Node
) : Node(MemType), Instruction {
    var beforeMem by operand(beforeMem)
    var address by operand(address)
    var value by operand(value)

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
    var left by operand(left)
    var right by operand(right)

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

    var target by operand(target)
    val arguments by operandList(arguments)

    val returns = funcType.returns.indices.map { project(it) }

    override fun fullString(namer: (Node) -> String): String {
        val t = target.untypedString(namer)
        val a = arguments.joinToString { it.typedString(namer) }
        val r = returns.joinToString { it.typedString(namer) }

        return "$r = $t($a)"
    }
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

    override fun untypedString(namer: (Node) -> String): String {
        return super.untypedString(namer)
    }
}

class PlaceHolder(type: Type) : Node(type)
