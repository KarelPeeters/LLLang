package language.optimizer.passes

import language.optimizer.testBeforeAfter
import org.junit.jupiter.api.Test

class DSETest {
    @Test
    fun recursion() {
        testBeforeAfter("dse_unusedRecursion.ir", DeadSignatureElimination)
    }
}