package language.ir.support

import language.ir.Node

class UserInfo(root: Node) {
    private val users: MutableMap<Node, MutableSet<Node>>

    init {
        val users = mutableMapOf<Node, MutableSet<Node>>()
        Visitor.visitNodes(root) { node ->
            val operands = node.operands()
            for (operand in operands)
                users.getOrPut(operand, ::mutableSetOf) += node
            operands
        }
        this.users = users
    }

    operator fun get(node: Node): Set<Node> = users[node] ?: emptySet()

    fun isUsed(node: Node): Boolean = users[node]?.isNotEmpty() ?: false

    fun allNodes(): Set<Node> = users.keys

    /** Replace all usages of [from] with [to], also updating this [UserInfo] */
    fun replaceNode(from: Node, to: Node) {
        check(from.replaceAble)

        val fromUsers = this.users[from] ?: return
        val toUsers = this.users.getOrPut(to, ::mutableSetOf)

        for (user in fromUsers)
            user.replaceOperand(from, to)

        toUsers.addAll(fromUsers)
        fromUsers.clear()
    }
}