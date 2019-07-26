package language.util

import language.util.TraverseOrder.BreadthFirst
import language.util.TraverseOrder.Unordered
import java.util.*

interface Graph<N> {
    val roots: List<N>
    fun children(node: N): List<N>
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
                TraverseOrder.DepthFirst -> children(curr).asReversed().forEach(toVisit::addFirst)
            }
        }
    }

    return reached
}