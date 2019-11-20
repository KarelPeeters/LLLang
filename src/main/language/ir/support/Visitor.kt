package language.ir.support

import language.ir.*
import language.ir.Function
import java.util.*

object Visitor {
    /**
     * Visit all nodes reachable trough [next] from [root] exactly once, returns a set of all visited nodes.
     */
    inline fun <N : Node> visitNodes(root: N, next: (N) -> Collection<N>): Set<N> {
        val visited = mutableSetOf<N>()
        val toVisit = ArrayDeque<N>()
        toVisit += root

        while (true) {
            val curr = toVisit.poll() ?: break
            if (visited.add(curr))
                toVisit += next(curr)
        }

        return visited
    }

    fun findNodes(program: Program): Set<Node> = visitNodes<Node>(program) { node -> node.operands() }

    fun findFunctions(program: Program): Set<Function> = visitNodes<Node>(program.entry) { node ->
        node.operands()
    }.filterIsInstanceTo(mutableSetOf())

    fun findInnerNodes(function: Function): Set<Node> = visitNodes<Node>(function) { node ->
        if (node == function || node !is Function)
            node.operands()
        else
            emptyList()
    }

    fun findRegions(function: Function): Set<Region> = visitNodes(function.entry) { region ->
        region.successors()
    }

    fun findPredecessors(function: Function): Map<Region, Set<Region>> {
        val regions = findRegions(function)
        val predecessors = regions.associateWith { mutableSetOf<Region>() }

        for (region in regions)
            for (successor in region.successors())
                predecessors.getValue(successor) += region

        return predecessors
    }

    fun findReturns(function: Function): Set<Return> =
            findRegions(function)
                    .map { it.terminator }
                    .filterIsInstanceTo(mutableSetOf())
}