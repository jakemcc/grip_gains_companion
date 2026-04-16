package app.grip_gains_companion.util

import app.grip_gains_companion.service.TargetFeedbackEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class TargetFeedbackSoundTest {

    @Test
    fun tooHeavyToneCanBeDisabledIndividually() {
        val calls = mutableListOf<String>()

        TargetFeedbackEvent.OffTarget(direction = 1.0).playEnabledTargetTone(
            settings = TargetSoundSettings(tooHeavyEnabled = false),
            playHigh = { calls += "high" },
            playLow = { calls += "low" },
            playOnTarget = { calls += "on-target" }
        )

        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun tooLightToneCanBeDisabledIndividually() {
        val calls = mutableListOf<String>()

        TargetFeedbackEvent.OffTarget(direction = -1.0).playEnabledTargetTone(
            settings = TargetSoundSettings(tooLightEnabled = false),
            playHigh = { calls += "high" },
            playLow = { calls += "low" },
            playOnTarget = { calls += "on-target" }
        )

        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun backOnTargetToneCanBeDisabledIndividually() {
        val calls = mutableListOf<String>()

        TargetFeedbackEvent.BackOnTarget.playEnabledTargetTone(
            settings = TargetSoundSettings(backOnTargetEnabled = false),
            playHigh = { calls += "high" },
            playLow = { calls += "low" },
            playOnTarget = { calls += "on-target" }
        )

        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun enabledTargetFeedbackStillPlaysTheMatchingTone() {
        val calls = mutableListOf<String>()

        listOf(
            TargetFeedbackEvent.OffTarget(direction = 1.0),
            TargetFeedbackEvent.OffTarget(direction = -1.0),
            TargetFeedbackEvent.BackOnTarget
        ).forEach { event ->
            event.playEnabledTargetTone(
                settings = TargetSoundSettings(),
                playHigh = { calls += "high" },
                playLow = { calls += "low" },
                playOnTarget = { calls += "on-target" }
            )
        }

        assertEquals(listOf("high", "low", "on-target"), calls)
    }

    @Test
    fun masterSoundSwitchDisablesAllTargetFeedbackTones() {
        val calls = mutableListOf<String>()

        TargetFeedbackEvent.OffTarget(direction = 1.0).playEnabledTargetTone(
            settings = TargetSoundSettings(masterEnabled = false),
            playHigh = { calls += "high" },
            playLow = { calls += "low" },
            playOnTarget = { calls += "on-target" }
        )

        assertEquals(emptyList<String>(), calls)
    }
}
