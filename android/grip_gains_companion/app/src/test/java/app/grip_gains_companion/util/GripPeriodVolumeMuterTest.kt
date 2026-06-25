package app.grip_gains_companion.util

import org.junit.Assert.assertEquals
import org.junit.Test

class GripPeriodVolumeMuterTest {

    @Test
    fun mutesFourSecondsAfterEnabledGripStartsAndRestoresPreviousVolumeWhenGripEnds() {
        val audio = FakeVolumeAccess(currentVolume = 7)
        val clock = FakeClock()
        val muter = GripPeriodVolumeMuter(audio, clock::now)

        muter.onGripActiveChanged(isGripActive = true, enabled = true)
        assertEquals(7, audio.currentVolume)

        clock.currentTime = 3_999L
        muter.onGripActiveChanged(isGripActive = true, enabled = true)
        assertEquals(7, audio.currentVolume)

        clock.currentTime = 4_000L
        muter.onGripActiveChanged(isGripActive = true, enabled = true)
        assertEquals(0, audio.currentVolume)

        muter.onGripActiveChanged(isGripActive = false, enabled = true)
        assertEquals(7, audio.currentVolume)
    }

    @Test
    fun keepsOriginalVolumeAcrossRepeatedActiveUpdates() {
        val audio = FakeVolumeAccess(currentVolume = 8)
        val clock = FakeClock()
        val muter = GripPeriodVolumeMuter(audio, clock::now)

        muter.onGripActiveChanged(isGripActive = true, enabled = true)
        clock.currentTime = 4_000L
        muter.onGripActiveChanged(isGripActive = true, enabled = true)
        audio.currentVolume = 3
        muter.onGripActiveChanged(isGripActive = true, enabled = true)

        muter.onGripActiveChanged(isGripActive = false, enabled = true)
        assertEquals(8, audio.currentVolume)
    }

    @Test
    fun restoresImmediatelyWhenDisabledDuringActiveGrip() {
        val audio = FakeVolumeAccess(currentVolume = 6)
        val clock = FakeClock()
        val muter = GripPeriodVolumeMuter(audio, clock::now)

        muter.onGripActiveChanged(isGripActive = true, enabled = true)
        clock.currentTime = 4_000L
        muter.onGripActiveChanged(isGripActive = true, enabled = true)
        muter.onGripActiveChanged(isGripActive = true, enabled = false)

        assertEquals(6, audio.currentVolume)
    }

    @Test
    fun doesNotMuteWhenGripEndsBeforeDelay() {
        val audio = FakeVolumeAccess(currentVolume = 9)
        val clock = FakeClock()
        val muter = GripPeriodVolumeMuter(audio, clock::now)

        muter.onGripActiveChanged(isGripActive = true, enabled = true)
        clock.currentTime = 2_000L
        muter.onGripActiveChanged(isGripActive = false, enabled = true)

        assertEquals(9, audio.currentVolume)
    }

    @Test
    fun doesNothingWhenDisabled() {
        val audio = FakeVolumeAccess(currentVolume = 5)
        val muter = GripPeriodVolumeMuter(audio)

        muter.onGripActiveChanged(isGripActive = true, enabled = false)
        muter.onGripActiveChanged(isGripActive = false, enabled = false)

        assertEquals(5, audio.currentVolume)
    }

    private class FakeVolumeAccess(
        override var currentVolume: Int
    ) : GripPeriodVolumeAccess {
        override fun mute() {
            currentVolume = 0
        }

        override fun restore(volume: Int) {
            currentVolume = volume
        }
    }

    private class FakeClock(var currentTime: Long = 0L) {
        fun now(): Long = currentTime
    }
}
