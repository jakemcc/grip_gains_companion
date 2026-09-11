package app.grip_gains_companion.debug

import kotlinx.coroutines.flow.StateFlow

interface DebugForceDataSource {
    val isAvailable: Boolean
    val isRunning: StateFlow<Boolean>
    val targetWeightKg: Double

    fun start()
    fun stop()
}
