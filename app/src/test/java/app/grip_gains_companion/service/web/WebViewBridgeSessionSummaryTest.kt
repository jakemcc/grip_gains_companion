package app.grip_gains_companion.service.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewBridgeSessionSummaryTest {

    @Test
    fun exposesSessionResultsVisibilityForSessionSummaryLifecycle() {
        val bridge = WebViewBridge()

        assertFalse(bridge.sessionResultsVisible.value)
        bridge.onSessionResultsVisibilityChanged(true)
        assertTrue(bridge.sessionResultsVisible.value)
        bridge.onSessionResultsVisibilityChanged(false)
        assertFalse(bridge.sessionResultsVisible.value)
    }
}
