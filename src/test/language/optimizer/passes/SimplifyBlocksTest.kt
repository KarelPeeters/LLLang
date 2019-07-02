package language.optimizer.passes

import language.optimizer.testBeforeAfter
import org.junit.jupiter.api.Test

class SimplifyBlocksTest {
    @Test
    fun selfLoop() = testBeforeAfter("simplifyBlocks_emptyEntry.ir", SimplifyBlocks)
}