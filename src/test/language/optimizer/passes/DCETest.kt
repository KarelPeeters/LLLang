package language.optimizer.passes

import language.optimizer.testBeforeAfter
import org.junit.jupiter.api.Test

class DCETest {
    @Test
    fun unusedRecursion() = testBeforeAfter("dce_unusedRecursion.ir", DeadCodeElimination)

    @Test
    fun funcAsValue() = testBeforeAfter("dce_funcAsValue.ir", DeadCodeElimination)

    @Test
    fun someUnusedParams() = testBeforeAfter("dce_someUnusedParams.ir", DeadCodeElimination)

    @Test
    fun unusedByOther() = testBeforeAfter("dce_unusedByOther.ir", DeadCodeElimination)

    @Test
    fun returnUnused() = testBeforeAfter("dce_returnUnused.ir", DeadCodeElimination)

    @Test
    fun returnAsUnusedParam() = testBeforeAfter("dce_returnAsUnusedParam.ir", DeadCodeElimination)

    @Test
    fun instrUsedBySelf() = testBeforeAfter("dce_instrUsedBySelf.ir", DeadCodeElimination)

    @Test
    fun instrParamCycle() = testBeforeAfter("dce_instrParamCycle.ir", DeadCodeElimination)

    @Test
    fun usedByTerm() = testBeforeAfter("dce_usedByTerm.ir", DeadCodeElimination)

    @Test
    fun storeInParam() = testBeforeAfter("dce_storeInParam.ir", DeadCodeElimination)
}