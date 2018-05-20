package egger.software.hmm

import egger.software.hmm.state.Tile.*
import egger.software.test.shouldBe
import egger.software.test.shouldThrow
import org.junit.Test

class UtilsSpec {

    @Test
    fun `from a list of StateWithProbability an element with a given offset can be picked`() {

        val statesWithProbability = listOf(Red withProbabilityOf 0.2, Green withProbabilityOf 0.6, Blue withProbabilityOf 0.2)

        statesWithProbability.selectStateAtOffset(0.5) shouldBe Green

        statesWithProbability.selectStateAtOffset(0.1) shouldBe Red
        statesWithProbability.selectStateAtOffset(0.0) shouldBe Red
        statesWithProbability.selectStateAtOffset(0.3) shouldBe Blue
        statesWithProbability.selectStateAtOffset(0.4) shouldBe Green

        shouldThrow<IllegalArgumentException> { statesWithProbability.selectStateAtOffset(-0.1) }
        shouldThrow<IllegalArgumentException> { statesWithProbability.selectStateAtOffset(1.0) }

    }

    @Test
    fun `mutable maps can be initialized with default values for a given set of keys`() {

        mutableMapOf<String, Int>().initUsing(setOf("A", "B"), 1) shouldBe
                mutableMapOf(
                        "A" to 1,
                        "B" to 1
                )

    }

}
