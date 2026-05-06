package app.grip_gains_companion.service

import app.grip_gains_companion.config.AppConstants
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressorHandlerTest {

    @Test
    fun emitsRepeatedOffTargetEventsWhileGripRemainsOutOfRange() = runBlocking {
        val handler = ProgressorHandler().apply {
            enableCalibration = false
            canEngage = true
            targetWeight = 20.0
            weightTolerance = 0.5
            engageThreshold = 3.0
            failThreshold = 1.0
        }

        val eventsDeferred = CompletableDeferred<List<TargetFeedbackEvent>>()
        val collectionJob = collectTargetFeedbackEvents(this, handler, 3, eventsDeferred)

        handler.processSample(rawWeight = 0.0, timestamp = 0L)
        handler.processSample(rawWeight = 21.0, timestamp = 1_000_000L)
        handler.processSample(rawWeight = 21.0, timestamp = 1_500_000L)
        handler.processSample(
            rawWeight = 21.0,
            timestamp = 1_000_000L + AppConstants.OFF_TARGET_FEEDBACK_INTERVAL_MS * 1_000
        )
        handler.processSample(
            rawWeight = 21.0,
            timestamp = 1_000_000L + AppConstants.OFF_TARGET_FEEDBACK_INTERVAL_MS * 2_000
        )

        val events = withTimeout(2_000) { eventsDeferred.await() }
        collectionJob.cancel()

        assertEquals(
            listOf(
                TargetFeedbackEvent.OffTarget(1.0),
                TargetFeedbackEvent.OffTarget(1.0),
                TargetFeedbackEvent.OffTarget(1.0)
            ),
            events
        )
    }

    @Test
    fun emitsBackOnTargetEventWhenGripReturnsWithinTolerance() = runBlocking {
        val handler = ProgressorHandler().apply {
            enableCalibration = false
            canEngage = true
            targetWeight = 20.0
            weightTolerance = 0.5
            engageThreshold = 3.0
            failThreshold = 1.0
        }

        val eventsDeferred = CompletableDeferred<List<TargetFeedbackEvent>>()
        val collectionJob = collectTargetFeedbackEvents(this, handler, 2, eventsDeferred)

        handler.processSample(rawWeight = 0.0, timestamp = 0L)
        handler.processSample(rawWeight = 21.0, timestamp = 1_000_000L)
        handler.processSample(rawWeight = 20.2, timestamp = 1_100_000L)

        val events = withTimeout(2_000) { eventsDeferred.await() }
        collectionJob.cancel()

        assertEquals(TargetFeedbackEvent.OffTarget(1.0), events[0])
        assertEquals(TargetFeedbackEvent.BackOnTarget, events[1])
    }

    @Test
    fun doesNotEmitBackOnTargetWhenNoTargetWeightIsConfigured() = runBlocking {
        val handler = ProgressorHandler().apply {
            enableCalibration = false
            canEngage = true
            weightTolerance = 0.5
            engageThreshold = 3.0
            failThreshold = 1.0
        }

        val eventsDeferred = CompletableDeferred<List<TargetFeedbackEvent>>()
        val collectionJob = collectTargetFeedbackEvents(this, handler, 1, eventsDeferred)

        handler.processSample(rawWeight = 0.0, timestamp = 0L)
        handler.processSample(rawWeight = 21.0, timestamp = 1_000_000L)
        handler.processSample(rawWeight = 20.2, timestamp = 1_100_000L)

        val timedOut = try {
            withTimeout(250) { eventsDeferred.await() }
            false
        } catch (_: TimeoutCancellationException) {
            true
        }
        collectionJob.cancel()

        assertTrue(timedOut)
    }

    @Test
    fun keepsActiveGripStateDuringReconnectGraceWindow() = runBlocking {
        val handler = ProgressorHandler().apply {
            enableCalibration = false
            canEngage = true
            enablePercentageThresholds = false
            engageThreshold = 3.0
            failThreshold = 1.0
        }

        val failedDeferred = CompletableDeferred<Unit>()
        val collectionJob = collectGripFailures(this, handler, failedDeferred)

        handler.processSample(rawWeight = 0.0, timestamp = 0L)
        handler.processSample(rawWeight = 10.0, timestamp = 1_000_000L)
        handler.onConnectionLost()
        handler.onConnectionRestored()
        handler.processSample(rawWeight = 0.0, timestamp = 1_500_000L)

        val timedOut = try {
            withTimeout(250) { failedDeferred.await() }
            false
        } catch (_: TimeoutCancellationException) {
            true
        }
        collectionJob.cancel()

        assertTrue(handler.engaged)
        assertEquals(0.0, handler.state.value.baseline, 0.0)
        assertTrue(timedOut)
    }

    @Test
    fun resumesFailureDetectionAfterReconnectGraceWindow() = runBlocking {
        val handler = ProgressorHandler().apply {
            enableCalibration = false
            canEngage = true
            enablePercentageThresholds = false
            engageThreshold = 3.0
            failThreshold = 1.0
        }

        val failedDeferred = CompletableDeferred<Unit>()
        val collectionJob = collectGripFailures(this, handler, failedDeferred)

        handler.processSample(rawWeight = 0.0, timestamp = 0L)
        handler.processSample(rawWeight = 10.0, timestamp = 1_000_000L)
        handler.onConnectionLost()
        handler.onConnectionRestored()
        handler.processSample(rawWeight = 0.0, timestamp = 1_500_000L)
        handler.processSample(rawWeight = 0.0, timestamp = 4_100_000L)

        withTimeout(2_000) { failedDeferred.await() }
        collectionJob.cancel()

        assertTrue(!handler.engaged)
    }

    private fun collectTargetFeedbackEvents(
        scope: kotlinx.coroutines.CoroutineScope,
        handler: ProgressorHandler,
        expectedCount: Int,
        eventsDeferred: CompletableDeferred<List<TargetFeedbackEvent>>
    ): Job {
        val collected = mutableListOf<TargetFeedbackEvent>()
        return scope.launch(start = CoroutineStart.UNDISPATCHED) {
            handler.targetFeedbackEvents.collect { event ->
                collected += event
                if (collected.size == expectedCount && !eventsDeferred.isCompleted) {
                    eventsDeferred.complete(collected.toList())
                }
            }
        }
    }

    private fun collectGripFailures(
        scope: kotlinx.coroutines.CoroutineScope,
        handler: ProgressorHandler,
        failedDeferred: CompletableDeferred<Unit>
    ): Job {
        return scope.launch(start = CoroutineStart.UNDISPATCHED) {
            handler.gripFailed.collect {
                if (!failedDeferred.isCompleted) {
                    failedDeferred.complete(Unit)
                }
            }
        }
    }
}
