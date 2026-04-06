package app.grip_gains_companion.util

enum class TonePreviewAction {
    Warning,
    TooHeavy,
    TooLight,
    BackOnTarget;

    fun play(
        playWarning: () -> Unit,
        playHigh: () -> Unit,
        playLow: () -> Unit,
        playOnTarget: () -> Unit
    ) {
        when (this) {
            Warning -> playWarning()
            TooHeavy -> playHigh()
            TooLight -> playLow()
            BackOnTarget -> playOnTarget()
        }
    }
}

fun playTonePreview(action: TonePreviewAction) {
    action.play(
        playWarning = ToneGenerator::playWarningTone,
        playHigh = ToneGenerator::playHighTone,
        playLow = ToneGenerator::playLowTone,
        playOnTarget = ToneGenerator::playOnTargetTone
    )
}
