package language.optimizer.passes

import language.optimizer.testBeforeAfter
import org.junit.jupiter.api.Test

class DSETest {
    @Test
    fun recursion() = testBeforeAfter("dse_unusedRecursion.ir", DeadSignatureElimination)

    @Test
    fun funcAsValue() = testBeforeAfter("dse_funcAsValue.ir", DeadSignatureElimination)

    @Test
    fun someUnusedParams() = testBeforeAfter("dse_someUnusedParams.ir", DeadSignatureElimination)
}