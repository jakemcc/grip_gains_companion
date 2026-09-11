package app.grip_gains_companion.service.ble

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PeriodicScanResetTest {

    @Test
    fun resetsEveryIntervalWhileEligibilityContinues() = runTest {
        val eligible = MutableStateFlow(true)
        var resetCount = 0

        backgroundScope.launch {
            eligible.collectPeriodicScanResets(resetPeriodMs = 3_000L) {
                resetCount++
            }
        }
        runCurrent()

        advanceTimeBy(3_000L)
        runCurrent()
        assertEquals(1, resetCount)

        advanceTimeBy(3_000L)
        runCurrent()
        assertEquals(2, resetCount)
    }

    @Test
    fun newEligiblePeriodGetsFullResetInterval() = runTest {
        val eligible = MutableStateFlow(false)
        var resetCount = 0

        backgroundScope.launch {
            eligible.collectPeriodicScanResets(resetPeriodMs = 3_000L) {
                resetCount++
            }
        }
        runCurrent()

        eligible.value = true
        runCurrent()
        advanceTimeBy(2_000L)

        eligible.value = false
        runCurrent()
        eligible.value = true
        runCurrent()
        advanceTimeBy(1_000L)
        runCurrent()

        assertEquals(0, resetCount)

        advanceTimeBy(2_000L)
        runCurrent()

        assertEquals(1, resetCount)
    }
}
