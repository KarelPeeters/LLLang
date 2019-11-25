package language.ir.support

import language.ir.Function
import language.ir.Node
import language.ir.Program
import language.ir.Region
import java.util.*

object Visitor {
    /**
     * Visit all nodes reachable trough [next] from [root] exactly once, returns the set of all visited nodes.
     */
    inline fun <N : Node> visitNodes(root: N, next: (N) -> Collection<N>): Set<N> = visitNodes(listOf(root), next)

    /**
     * Visit all nodes reachable trough [next] from [roots] exactly once, returns the set of all visited nodes.
     */
    inline fun <N : Node> visitNodes(roots: Collection<N>, next: (N) -> Collection<N>): Set<N> {
        val visited = mutableSetOf<N>()
        val toVisit = ArrayDeque<N>()
        toVisit.addAll(roots)

        while (true) {
            val curr = toVisit.poll() ?: break
            if (visited.add(curr))
                toVisit += next(curr)
        }

        return visited
    }

    fun findNodes(program: Program): Set<Node> = visitNodes<Node>(program) { node -> node.operands() }

    fun findFunctions(program: Program): Set<Function> = visitNodes<Node>(program.end) { node ->
        node.operands()
    }.filterIsInstanceTo(mutableSetOf())

    fun findInnerNodes(function: Function): Set<Node> = visitNodes<Node>(function) { node ->
        if (node == function || node !is Function)
            node.operands()
        else
            emptyList()
    }

    fun findRegions(function: Function): Set<Region> {
        return visitNodes(function.endRegions()) { node ->
            node.predecessors.mapNotNull { it.from }
        }
    }
}