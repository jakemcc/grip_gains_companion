package app.grip_gains_companion.service

import app.grip_gains_companion.config.AppConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundInactivityShutdownTimerTest {

    @Test
    fun schedulesShutdownAfterBackgroundInactivityTimeout() {
        val scheduledTimeouts = mutableListOf<Runnable>()
        var shutdownCount = 0
        val timer = BackgroundInactivityShutdownTimer(
            postDelayed = { runnable, delayMs ->
                assertEquals(AppConstants.BACKGROUND_INACTIVITY_TIMEOUT_MS, delayMs)
                scheduledTimeouts += runnable
            },
            removeCallbacks = { runnable -> scheduledTimeouts.remove(runnable) },
            shutdown = { shutdownCount++ }
        )

        timer.onEnteredBackground()

        assertEquals(1, scheduledTimeouts.size)

        scheduledTimeouts.single().run()

        assertEquals(1, shutdownCount)
        assertTrue(scheduledTimeouts.isEmpty())
    }

    @Test
    fun cancelsPendingShutdownWhenAppReturnsForeground() {
        val scheduledTimeouts = mutableListOf<Runnable>()
        var shutdownCount = 0
        val timer = BackgroundInactivityShutdownTimer(
            postDelayed = { runnable, _ -> scheduledTimeouts += runnable },
            removeCallbacks = { runnable -> scheduledTimeouts.remove(runnable) },
            shutdown = { shutdownCount++ }
        )

        timer.onEnteredBackground()
        timer.onEnteredForeground()

        assertTrue(scheduledTimeouts.isEmpty())
        assertEquals(0, shutdownCount)
    }
}
