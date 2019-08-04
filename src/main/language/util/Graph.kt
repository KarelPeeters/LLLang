package language.util

import language.util.TraverseOrder.*
import java.util.*

interface Graph<N> {
    val roots: Collection<N>
    fun children(node: N): Collection<N>
}

enum class TraverseOrder {
    DepthFirst,
    BreadthFirst,
    Unordered,
}

/**
 * Find all reachable nodes. The returned set is ordered according to [order].
 */
fun <N> Graph<N>.reachable(order: TraverseOrder = Unordered): Set<N> {
    val toVisit = ArrayDeque<N>()
    toVisit.addAll(this.roots)

    val reached: MutableSet<N> = when (order) {
        Unordered -> hashSetOf()
        else -> linkedSetOf()
    }

    while (true) {
        val curr = toVisit.poll() ?: break

        if (reached.add(curr)) {
            when (order) {
                BreadthFirst, Unordered -> toVisit.addAll(children(curr))
                DepthFirst -> children(curr).reversed().forEach(toVisit::addFirst)
            }
        }
    }

    return reached
}