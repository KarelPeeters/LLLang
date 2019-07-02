package language.optimizer.passes

import language.optimizer.testBeforeAfter
import org.junit.jupiter.api.Test

class SCCPTest {
    @Test
    fun loopSame() = testBeforeAfter("sccp_loopSame.ir", SCCP, DeadInstructionElimination)

    @Test
    fun loopDifferent() = testBeforeAfter("sccp_loopDifferent.ir", SCCP)
}