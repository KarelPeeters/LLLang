package language.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GraphTest {
    @Test
    fun reached() {
        val map = mutableMapOf(
                1 to listOf(2, 3),
                2 to listOf(1, 4, 5),
                3 to listOf(1, 5, 6)
        )

        val reached = object : Graph<Int> {
            override val roots = map.keys
            override fun children(node: Int) = map[node] ?: emptyList()
        }.reached()

        assertEquals(listOf(1, 2, 3, 4, 5, 6), reached.toList()) { "must be breath-first" }
    }
}