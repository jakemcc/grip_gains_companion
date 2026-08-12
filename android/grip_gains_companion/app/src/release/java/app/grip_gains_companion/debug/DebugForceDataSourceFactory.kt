package app.grip_gains_companion.debug

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

fun createDebugForceDataSource(
    scope: CoroutineScope,
    onSample: suspend (forceKg: Double, timestampMicros: Long) -> Unit
): DebugForceDataSource = UnavailableDebugForceDataSource

private object UnavailableDebugForceDataSource : DebugForceDataSource {
    override val isAvailable: Boolean = false
    override val targetWeightKg: Double = 0.0

    private val running = MutableStateFlow(false)
    override val isRunning: StateFlow<Boolean> = running.asStateFlow()

    override fun start() = Unit
    override fun stop() = Unit
}
