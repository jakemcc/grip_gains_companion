package app.grip_gains_companion.ui.components

data class GripMuteToggleVisualState(
    val contentDescription: String,
    val statusText: String
)

enum class QuickAction {
    MUTE,
    SETTINGS
}

data class GripQuickActionLayout(
    val leading: QuickAction,
    val trailing: QuickAction
)

data class GripFloatingQuickActionPlacement(
    val horizontalInsetDp: Int,
    val topInsetDp: Int
)

fun gripQuickActionLayout() = GripQuickActionLayout(
    leading = QuickAction.MUTE,
    trailing = QuickAction.SETTINGS
)

fun gripFloatingQuickActionPlacement() = GripFloatingQuickActionPlacement(
    // Leave the website's hamburger and notifications touch targets unobstructed.
    horizontalInsetDp = 72,
    topInsetDp = 16
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
