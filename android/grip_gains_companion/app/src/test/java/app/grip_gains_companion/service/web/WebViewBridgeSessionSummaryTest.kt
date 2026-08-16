package app.grip_gains_companion.service.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewBridgeSessionSummaryTest {

    @Test
    fun exposesSaveButtonVisibilityForSessionSummaryLifecycle() {
        val bridge = WebViewBridge()

        assertFalse(bridge.saveButtonVisible.value)
        bridge.onSaveButtonVisibilityChanged(true)
        assertTrue(bridge.saveButtonVisible.value)
        bridge.onSaveButtonVisibilityChanged(false)
        assertFalse(bridge.saveButtonVisible.value)
    }
}
