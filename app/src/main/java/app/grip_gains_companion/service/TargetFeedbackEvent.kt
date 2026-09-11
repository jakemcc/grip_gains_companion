package app.grip_gains_companion.service

sealed interface TargetFeedbackEvent {
    data class OffTarget(val direction: Double) : TargetFeedbackEvent
    data object BackOnTarget : TargetFeedbackEvent
}
