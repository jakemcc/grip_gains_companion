package app.grip_gains_companion.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class GripMuteToggleVisualStateTest {

    @Test
    fun describesEnabledMuteState() {
        val state = gripMuteToggleVisualState(mutePhoneDuringGrip = true)

        assertEquals("Mute during grip on", state.contentDescription)
        assertEquals("Phone audio will mute during grip", state.statusText)
    }

    @Test
    fun describesDisabledMuteState() {
        val state = gripMuteToggleVisualState(mutePhoneDuringGrip = false)

        assertEquals("Mute during grip off", state.contentDescription)
        assertEquals("Phone audio stays on during grip", state.statusText)
    }
}
