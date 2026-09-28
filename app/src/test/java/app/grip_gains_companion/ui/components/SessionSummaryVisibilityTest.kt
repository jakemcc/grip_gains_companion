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
                sessionResultsVisible = true,
                hasRepResults = true
            )
        )
    }

    @Test
    fun enabledSummaryOnlyAppearsAtSessionEndWithResults() {
        assertTrue(
            shouldShowEndOfSessionSummary(
                enabled = true,
                sessionResultsVisible = true,
                hasRepResults = true
            )
        )
        assertFalse(
            shouldShowEndOfSessionSummary(
                enabled = true,
                sessionResultsVisible = false,
                hasRepResults = true
            )
        )
        assertFalse(
            shouldShowEndOfSessionSummary(
                enabled = true,
                sessionResultsVisible = true,
                hasRepResults = false
            )
        )
    }
}
