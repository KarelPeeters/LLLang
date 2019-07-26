package language.util

import language.util.TraverseOrder.BreadthFirst
import language.util.TraverseOrder.DepthFirst
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GraphTest {
    val basicGraph = object : Graph<Int> {
        val map = mutableMapOf(
                1 to listOf(2, 3),
                2 to listOf(1, 4, 5),
                3 to listOf(1, 5, 6)
        )

        override val roots = listOf(1, 2)
        override fun children(node: Int) = map[node] ?: emptyList()
    }

    @Test
    fun reachedBreadthFirst() {
        assertEquals(listOf(1, 2, 3, 4, 5, 6), basicGraph.reachable(BreadthFirst).toList()) {
            "must contain all nodes and be ordered breadth-first"
        }
    }

    @Test
    fun reachedDepthFirst() {
        assertEquals(listOf(1, 2, 4, 5, 3, 6), basicGraph.reachable(DepthFirst).toList()) {
            "must contain all nodes and be depth-first"
        }
    }

    @Test
    fun reachedUnordered() {
        assertEquals(setOf(1, 2, 4, 5, 3, 6), basicGraph.reachable(DepthFirst)) {
            "must contain all nodes"
        }
    }
}