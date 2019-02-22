package language.optimizer

import java.util.*

interface Graph<N> {
    val roots: Collection<N>
    fun children(node: N): Collection<N>
}

fun <N> Graph<N>.reached(): Set<N> {
    val toVisit: Queue<N> = ArrayDeque()
    toVisit.addAll(this.roots)
    val reached = mutableSetOf<N>()

    while (toVisit.isNotEmpty()) {
        val curr = toVisit.poll()
        if (reached.add(curr))
            toVisit.addAll(this.children(curr))
    }

    return reached
}