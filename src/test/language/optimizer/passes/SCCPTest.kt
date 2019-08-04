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
    fun nonConstantParam() = testBeforeAfter("sccp_nonConstantParam.ir", ProgramSCCP)

    @Test
    fun constantReturn() = testBeforeAfter("sccp_constantReturn.ir", ProgramSCCP)

    @Test
    fun nonConstantReturn() = testBeforeAfter("sccp_nonConstantReturn.ir", ProgramSCCP)

    @Test
    fun deadCode() = testBeforeAfter("sccp_deadCode.ir", ProgramSCCP)

    @Test
    fun funcAsValue() = testBeforeAfter("sccp_funcAsValue.ir", ProgramSCCP)

    @Test
    fun funcAsValueDead() = testBeforeAfter("sccp_funcAsValueDead.ir", ProgramSCCP)

    @Test
    fun passTroughFunc() = testBeforeAfter("sccp_passTroughFunc.ir", ProgramSCCP)

    @Test
    fun multiFuncLoop() = testBeforeAfter("sccp_multiFuncLoop.ir", ProgramSCCP)

    @Test
    fun sumLoop() = testBeforeAfter("sccp_sumLoop.ir", ProgramSCCP)
}