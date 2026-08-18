package app.grip_gains_companion.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSummaryVisibilityTest {

    @Test
    fun summaryRequiresTheDedicatedSetting() {
        assertFalse(
            shouldShowEndOfSessionSummary(
                enabled = false,
                saveButtonVisible = true,
                hasRepResults = true
            )
        )
    }

    @Test
    fun enabledSummaryOnlyAppearsAtSessionEndWithResults() {
        assertTrue(
            shouldShowEndOfSessionSummary(
                enabled = true,
                saveButtonVisible = true,
                hasRepResults = true
            )
        )
        assertFalse(
            shouldShowEndOfSessionSummary(
                enabled = true,
                saveButtonVisible = false,
                hasRepResults = true
            )
        )
        assertFalse(
            shouldShowEndOfSessionSummary(
                enabled = true,
                saveButtonVisible = true,
                hasRepResults = false
            )
        )
    }
}
