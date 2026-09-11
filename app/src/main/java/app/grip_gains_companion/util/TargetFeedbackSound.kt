package app.grip_gains_companion.util

import app.grip_gains_companion.service.TargetFeedbackEvent

data class TargetSoundSettings(
    val masterEnabled: Boolean = true,
    val tooHeavyEnabled: Boolean = true,
    val tooLightEnabled: Boolean = true,
    val backOnTargetEnabled: Boolean = true,
    val spokenDirectionsEnabled: Boolean = true
)

fun TargetFeedbackEvent.playEnabledTargetFeedback(
    settings: TargetSoundSettings,
    playHigh: () -> Unit,
    playLow: () -> Unit,
    playOnTarget: () -> Unit,
    speak: (String) -> Boolean
) {
    if (!settings.masterEnabled) return

    when (this) {
        is TargetFeedbackEvent.OffTarget -> {
            if (direction > 0 && settings.tooHeavyEnabled) {
                if (settings.spokenDirectionsEnabled) {
                    if (!speak("Less")) playHigh()
                } else {
                    playHigh()
                }
            } else if (direction <= 0 && settings.tooLightEnabled) {
                if (settings.spokenDirectionsEnabled) {
                    if (!speak("More")) playLow()
                } else {
                    playLow()
                }
            }
        }
        TargetFeedbackEvent.BackOnTarget -> {
            if (settings.backOnTargetEnabled) {
                playOnTarget()
            }
        }
    }
}

fun TargetFeedbackEvent.playEnabledTargetTone(
    settings: TargetSoundSettings,
    playHigh: () -> Unit,
    playLow: () -> Unit,
    playOnTarget: () -> Unit
) {
    if (!settings.masterEnabled) return

    when (this) {
        is TargetFeedbackEvent.OffTarget -> {
            if (direction > 0 && settings.tooHeavyEnabled) {
                playHigh()
            } else if (direction <= 0 && settings.tooLightEnabled) {
                playLow()
            }
        }
        TargetFeedbackEvent.BackOnTarget -> {
            if (settings.backOnTargetEnabled) {
                playOnTarget()
            }
        }
    }
}
