package app.grip_gains_companion.service.web

import org.junit.Assert.assertTrue
import org.junit.Test

class JavaScriptBridgeTest {

    @Test
    fun observerScriptsWatchDocumentBodySoTheySurviveReplacedTimerNodes() {
        val scripts = listOf(
            JavaScriptBridge.observerScript,
            JavaScriptBridge.targetWeightObserverScript,
            JavaScriptBridge.remainingTimeObserverScript
        )

        scripts.forEach { script ->
            assertTrue(
                "Expected observer script to observe document.body: $script",
                script.contains("observer.observe(document.body")
            )
        }
    }

    @Test
    fun observerScriptsAreIdempotentWhenInjectedMoreThanOnce() {
        val scripts = listOf(
            JavaScriptBridge.observerScript,
            JavaScriptBridge.targetWeightObserverScript,
            JavaScriptBridge.remainingTimeObserverScript
        )

        scripts.forEach { script ->
            assertTrue(
                "Expected observer script to guard duplicate installation: $script",
                script.contains("window.__gripGainsCompanion")
            )
        }
    }
}
