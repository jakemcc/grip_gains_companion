package app.grip_gains_companion.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TonePreviewActionTest {

    @Test
    fun warningPreviewUsesWarningTone() {
        val calls = mutableListOf<String>()

        TonePreviewAction.Warning.play(
            playWarning = { calls += "warning" },
            playHigh = { calls += "high" },
            playLow = { calls += "low" },
            playOnTarget = { calls += "on-target" }
        )

        assertEquals(listOf("warning"), calls)
    }

    @Test
    fun tooHeavyPreviewUsesHighTone() {
        val calls = mutableListOf<String>()

        TonePreviewAction.TooHeavy.play(
            playWarning = { calls += "warning" },
            playHigh = { calls += "high" },
            playLow = { calls += "low" },
            playOnTarget = { calls += "on-target" }
        )

        assertEquals(listOf("high"), calls)
    }

    @Test
    fun tooLightPreviewUsesLowTone() {
        val calls = mutableListOf<String>()

        TonePreviewAction.TooLight.play(
            playWarning = { calls += "warning" },
            playHigh = { calls += "high" },
            playLow = { calls += "low" },
            playOnTarget = { calls += "on-target" }
        )

        assertEquals(listOf("low"), calls)
    }

    @Test
    fun backOnTargetPreviewUsesOnTargetTone() {
        val calls = mutableListOf<String>()

        TonePreviewAction.BackOnTarget.play(
            playWarning = { calls += "warning" },
            playHigh = { calls += "high" },
            playLow = { calls += "low" },
            playOnTarget = { calls += "on-target" }
        )

        assertEquals(listOf("on-target"), calls)
    }
}
