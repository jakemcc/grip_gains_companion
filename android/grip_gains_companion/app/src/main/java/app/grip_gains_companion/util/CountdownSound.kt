package app.grip_gains_companion.util

class CountdownSound(
    private val countdownStartSeconds: Int = 5,
    private val playSecond: (Int) -> Unit
) {
    private val countdownRange = 0..countdownStartSeconds
    private val playedSeconds = mutableSetOf<Int>()
    private var lastRemainingTime: Int? = null

    fun onRemainingTimeChanged(seconds: Int?) {
        val previousRemainingTime = lastRemainingTime

        if (seconds == null) {
            reset()
            lastRemainingTime = null
            return
        }

        if (
            seconds > countdownStartSeconds ||
            (previousRemainingTime != null && seconds > previousRemainingTime && seconds in countdownRange)
        ) {
            reset()
        }

        if (seconds in countdownRange && playedSeconds.add(seconds)) {
            playSecond(seconds)
        }

        lastRemainingTime = seconds
    }

    private fun reset() {
        playedSeconds.clear()
    }
}
