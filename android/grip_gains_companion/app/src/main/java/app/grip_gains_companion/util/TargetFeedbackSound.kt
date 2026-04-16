package app.grip_gains_companion.util

import app.grip_gains_companion.service.TargetFeedbackEvent

data class TargetSoundSettings(
    val masterEnabled: Boolean = true,
    val tooHeavyEnabled: Boolean = true,
    val tooLightEnabled: Boolean = true,
    val backOnTargetEnabled: Boolean = true
)

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
