package app.grip_gains_companion.service

import app.grip_gains_companion.config.AppConstants

class BackgroundInactivityShutdownTimer(
    private val postDelayed: (Runnable, Long) -> Unit,
    private val removeCallbacks: (Runnable) -> Unit,
    private val shutdown: () -> Unit
) {
    private var timeoutRunnable: Runnable? = null

    fun onEnteredBackground() {
        onEnteredForeground()

        val timeout = Runnable {
            val current = timeoutRunnable ?: return@Runnable
            timeoutRunnable = null
            removeCallbacks(current)
            shutdown()
        }
        timeoutRunnable = timeout
        postDelayed(timeout, AppConstants.BACKGROUND_INACTIVITY_TIMEOUT_MS)
    }

    fun onEnteredForeground() {
        timeoutRunnable?.let(removeCallbacks)
        timeoutRunnable = null
    }
}
