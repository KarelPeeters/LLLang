package language.optimizer.passes

import language.optimizer.readProgram
import language.optimizer.runPass
import org.junit.jupiter.api.Test

class DFOTest {
    @Test
    fun deadButUsed() {
        val program = this::class.readProgram("dfo_deadButUsed.ir")
        //make sure it doesn't crash
        runPass(DeadFunctionElimination, program)
    }
}