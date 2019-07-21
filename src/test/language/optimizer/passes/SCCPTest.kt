package language.optimizer.passes

import language.optimizer.testBeforeAfter
import org.junit.jupiter.api.Test

class SCCPTest {
    @Test
    fun loopSame() = testBeforeAfter("sccp_loopSame.ir", SCCP, DeadInstructionElimination)

    @Test
    fun loopDifferent() = testBeforeAfter("sccp_loopDifferent.ir", SCCP)

    @Test
    fun undef() = testBeforeAfter("sccp_undef.ir", SCCP, DeadInstructionElimination)

    @Test
    fun onlyUndef() = testBeforeAfter("sccp_onlyUndef.ir", SCCP)

    @Test
    fun constantParam() = testBeforeAfter("sccp_constantParam.ir", ProgramSCCP)

    @Test
    fun deadCode() = testBeforeAfter("sccp_deadCode.ir", ProgramSCCP)

    @Test
    fun funcAsValue() = testBeforeAfter("sccp_funcAsValue.ir", ProgramSCCP)

    @Test
    fun passTroughFunc() = testBeforeAfter("sccp_passTroughFunc.ir", ProgramSCCP)
}