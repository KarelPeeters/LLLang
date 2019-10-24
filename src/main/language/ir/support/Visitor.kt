package language.ir.support

import language.ir.Node
import java.util.*

/**
 * Visit all nodes reachable trough [Node.operands] from [root] exactly once.
 * The return value of [action] decides whether to continue the search trough the given node.
 */
inline fun visitNodes(root: Node, action: (Node) -> Boolean = { true }): Set<Node> {
    val visited = mutableSetOf<Node>()
    val toVisit = ArrayDeque<Node>()
    toVisit += root

    while (true) {
        val curr = toVisit.poll() ?: break
        if (visited.add(curr)) {
            if (action(curr)) {
                val operands = curr.operands()
                toVisit += operands
            }
        }
    }

    return visited
}
