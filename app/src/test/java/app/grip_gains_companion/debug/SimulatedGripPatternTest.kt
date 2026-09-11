package app.grip_gains_companion.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulatedGripPatternTest {

    @Test
    fun warmupRemainsUnloadedLongEnoughForCalibration() {
        assertEquals(0.0, SimulatedGripPattern.forceKgAt(0), 0.0)
        assertEquals(0.0, SimulatedGripPattern.forceKgAt(5_000), 0.0)
    }

    @Test
    fun firstRepRampsHoldsAndReleases() {
        assertEquals(0.0, SimulatedGripPattern.forceKgAt(5_500), 0.0)
        assertEquals(10.0, SimulatedGripPattern.forceKgAt(5_875), 0.001)

        val holdForces = (6_500L..11_500L step 100L).map(SimulatedGripPattern::forceKgAt)
        assertTrue(holdForces.all { it in 19.4..20.6 })
        assertTrue(holdForces.distinct().size > 10)

        assertEquals(0.0, SimulatedGripPattern.forceKgAt(12_750), 0.001)
    }

    @Test
    fun repPatternRepeatsDeterministically() {
        val firstRepHold = SimulatedGripPattern.forceKgAt(7_250)
        val secondRepHold = SimulatedGripPattern.forceKgAt(17_250)

        assertEquals(firstRepHold, secondRepHold, 0.0)
    }
}
