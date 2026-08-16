package app.grip_gains_companion.service

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressorSessionStatisticsTest {

    @Test
    fun recordsAverageAndStandardDeviationForEachCompletedRep() = runBlocking {
        val handler = configuredHandler()

        completeRep(handler, startTimestamp = 1_000_000L, forces = listOf(10.0, 12.0))
        completeRep(handler, startTimestamp = 4_000_000L, forces = listOf(18.0, 20.0, 22.0))

        val results = handler.sessionRepResults.value
        assertEquals(2, results.size)
        assertEquals(11.0, results[0].meanForce, 0.001)
        assertEquals(1.0, results[0].stdDev, 0.001)
        assertEquals(20.0, results[1].meanForce, 0.001)
        assertEquals(1.633, results[1].stdDev, 0.001)
    }

    @Test
    fun excludesReleaseSampleFromCompletedRepStatistics() = runBlocking {
        val handler = configuredHandler()

        completeRep(handler, startTimestamp = 1_000_000L, forces = listOf(10.0, 12.0))

        assertEquals(listOf(10.0, 12.0), handler.sessionRepResults.value.single().samples)
    }

    @Test
    fun clearingSessionResultsDoesNotPersistPreviousReps() = runBlocking {
        val handler = configuredHandler()
        completeRep(handler, startTimestamp = 1_000_000L, forces = listOf(10.0, 12.0))

        handler.clearSessionRepResults()

        assertTrue(handler.sessionRepResults.value.isEmpty())
    }

    private fun configuredHandler() = ProgressorHandler().apply {
        enableCalibration = false
        enablePercentageThresholds = false
        engageThreshold = 3.0
        failThreshold = 1.0
        targetWeight = 20.0
        canEngage = true
    }

    private suspend fun completeRep(
        handler: ProgressorHandler,
        startTimestamp: Long,
        forces: List<Double>
    ) {
        if (handler.waitingForSamples) {
            handler.processSample(rawWeight = 0.0, timestamp = 0L)
        }
        forces.forEachIndexed { index, force ->
            handler.processSample(
                rawWeight = force,
                timestamp = startTimestamp + index * 100_000L
            )
        }
        handler.processSample(
            rawWeight = 0.0,
            timestamp = startTimestamp + forces.size * 100_000L
        )
    }
}
