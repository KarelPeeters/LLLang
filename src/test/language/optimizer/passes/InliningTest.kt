package language.optimizer.passes

import language.optimizer.testBeforeAfter
import org.junit.jupiter.api.Test

class InliningTest {
    @Test
    fun usedAsArg() = testBeforeAfter("inlining_usedAsArg.ir", FunctionInlining)

    @Test
    fun multipleReturns() = testBeforeAfter("inlining_multipleReturns.ir", FunctionInlining)
}