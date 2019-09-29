package language.optimizer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DominatorInfoTest {
    @Test
    fun unreachable() {
        val func = this::class.readFunction("dom_unreachable.ir")
        val info = DominatorInfo(func)
        val (entry, other) = func.blocks

        assertEquals(setOf(entry), info.dominators(entry)) { "entry is only dominated by itself" }
        assertEquals(setOf(entry, other), info.dominators(other)) { "unreachable blocks are dominated by everything" }

        assertNull(info.parent(entry)) { "entry has no parent" }
        assertNull(info.parent(other)) { "unreachable blocks have no parent" }
    }

    @Test
    fun unreachableLoop() {
        val func = this::class.readFunction("dom_unreachableLoop.ir")
        val info = DominatorInfo(func)
        val (entry, first, second) = func.blocks

        assertEquals(setOf(entry), info.dominators(entry)) { "entry is only dominated by itself" }
        assertEquals(setOf(entry, first, second), info.dominators(first)) { "unreachable blocks are dominated by everything" }
        assertEquals(setOf(entry, first, second), info.dominators(second)) { "unreachable blocks are dominated by everything" }

        assertNull(info.parent(entry)) { "entry has no parent" }
        assertNull(info.parent(first)) { "unreachable blocks have no parent" }
        assertNull(info.parent(second)) { "unreachable blocks have no parent" }
    }
}