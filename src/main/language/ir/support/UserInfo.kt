package language.ir.support

import language.ir.Node

class UserInfo(root: Node) {
    private val users: Map<Node, Set<Node>>

    init {
        val users = mutableMapOf<Node, MutableSet<Node>>()
        visitNodes(root) { node ->
            for (operand in node.operands())
                users.getOrPut(operand, ::mutableSetOf) += node
            true
        }
        this.users = users
    }

    fun isUsed(node: Node): Boolean = users[node]?.isNotEmpty() ?: false

    fun users(node: Node): Set<Node> = users[node] ?: emptySet()

    fun allNodes(): Set<Node> = users.keys

    fun replaceWith(from: Node, to: Node) {
        check(from.replaceAble && to.replaceAble)
        for (user in users(from))
            user.replaceOperand(from, to)
    }
}