package app.grip_gains_companion.ui.components

data class GripMuteToggleVisualState(
    val contentDescription: String,
    val statusText: String
)

fun gripMuteToggleVisualState(mutePhoneDuringGrip: Boolean): GripMuteToggleVisualState {
    return if (mutePhoneDuringGrip) {
        GripMuteToggleVisualState(
            contentDescription = "Mute during grip on",
            statusText = "Phone audio will mute during grip"
        )
    } else {
        GripMuteToggleVisualState(
            contentDescription = "Mute during grip off",
            statusText = "Phone audio stays on during grip"
        )
    }
}
