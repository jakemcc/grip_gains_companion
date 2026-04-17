package app.grip_gains_companion.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CountdownSoundTest {

    @Test
    fun playsCountdownOnceForEachSecondFromFiveToZero() {
        val calls = mutableListOf<Int>()
        val countdownSound = CountdownSound(playSecond = { calls += it })

        listOf(20, 6, 5, 5, 4, 3, 2, 1, 0, -1).forEach {
            countdownSound.onRemainingTimeChanged(it)
        }

        assertEquals(listOf(5, 4, 3, 2, 1, 0), calls)
    }

    @Test
    fun resetsCountdownAfterTimerReturnsAboveFiveSeconds() {
        val calls = mutableListOf<Int>()
        val countdownSound = CountdownSound(playSecond = { calls += it })

        listOf(5, 4, 20, 5, 4).forEach {
            countdownSound.onRemainingTimeChanged(it)
        }

        assertEquals(listOf(5, 4, 5, 4), calls)
    }

    @Test
    fun nullRemainingTimeClearsCountdownState() {
        val calls = mutableListOf<Int>()
        val countdownSound = CountdownSound(playSecond = { calls += it })

        countdownSound.onRemainingTimeChanged(5)
        countdownSound.onRemainingTimeChanged(null)
        countdownSound.onRemainingTimeChanged(5)

        assertEquals(listOf(5, 5), calls)
    }
}
