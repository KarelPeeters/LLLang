package language.util

import java.util.*
import kotlin.collections.LinkedHashSet

interface Graph<N> {
    val roots: Collection<N>
    fun children(node: N): Collection<N>
}

/**
 * Find all reachable nodes. The returned set is ordered breadth-first.
 */
fun <N> Graph<N>.reached(): Set<N> {
    val toVisit: Deque<N> = ArrayDeque()
    toVisit.addAll(this.roots)
    val reached = LinkedHashSet<N>()

    while (toVisit.isNotEmpty()) {
        val curr = toVisit.poll()
        if (reached.add(curr))
            toVisit.addAll(this.children(curr))
    }

    return reached
}