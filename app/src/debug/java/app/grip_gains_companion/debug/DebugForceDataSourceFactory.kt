package app.grip_gains_companion.debug

import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

fun createDebugForceDataSource(
    scope: CoroutineScope,
    onSample: suspend (forceKg: Double, timestampMicros: Long) -> Unit
): DebugForceDataSource = EmulatorForceDataSource(
    scope = scope,
    onSample = onSample,
    isAvailable = isProbablyEmulator()
)

internal object SimulatedGripPattern {
    const val TARGET_WEIGHT_KG = 20.0
    private const val WARMUP_MS = 5_500L
    private const val REP_CYCLE_MS = 10_000L
    private const val RAMP_MS = 750L
    private const val HOLD_END_MS = 6_750L
    private const val RELEASE_END_MS = 7_250L

    fun forceKgAt(elapsedMs: Long): Double {
        if (elapsedMs < WARMUP_MS) return 0.0

        val repElapsedMs = (elapsedMs - WARMUP_MS) % REP_CYCLE_MS
        return when {
            repElapsedMs < RAMP_MS -> {
                TARGET_WEIGHT_KG * repElapsedMs.toDouble() / RAMP_MS
            }

            repElapsedMs < HOLD_END_MS -> {
                val holdSeconds = (repElapsedMs - RAMP_MS) / 1_000.0
                TARGET_WEIGHT_KG +
                    sin(holdSeconds * 2.0 * PI * 1.15) * 0.32 +
                    sin(holdSeconds * 2.0 * PI * 0.23) * 0.14
            }

            repElapsedMs < RELEASE_END_MS -> {
                val releaseProgress =
                    (repElapsedMs - HOLD_END_MS).toDouble() / (RELEASE_END_MS - HOLD_END_MS)
                TARGET_WEIGHT_KG * (1.0 - releaseProgress)
            }

            else -> 0.0
        }
    }
}

private class EmulatorForceDataSource(
    private val scope: CoroutineScope,
    private val onSample: suspend (forceKg: Double, timestampMicros: Long) -> Unit,
    override val isAvailable: Boolean
) : DebugForceDataSource {
    override val targetWeightKg: Double = SimulatedGripPattern.TARGET_WEIGHT_KG

    private val _isRunning = MutableStateFlow(false)
    override val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private var sampleJob: Job? = null

    override fun start() {
        if (!isAvailable || sampleJob?.isActive == true) return

        _isRunning.value = true
        sampleJob = scope.launch {
            val startedAtMs = SystemClock.elapsedRealtime()
            try {
                while (isActive) {
                    val nowMs = SystemClock.elapsedRealtime()
                    onSample(
                        SimulatedGripPattern.forceKgAt(nowMs - startedAtMs),
                        nowMs * 1_000L
                    )
                    delay(50L)
                }
            } finally {
                _isRunning.value = false
            }
        }
    }

    override fun stop() {
        sampleJob?.cancel()
        sampleJob = null
        _isRunning.value = false
    }
}

private fun isProbablyEmulator(): Boolean {
    return Build.FINGERPRINT.startsWith("generic") ||
        Build.FINGERPRINT.contains("emulator") ||
        Build.FINGERPRINT.contains("vbox") ||
        Build.MODEL.contains("Emulator") ||
        Build.MODEL.contains("Android SDK built for") ||
        Build.MANUFACTURER.contains("Genymotion") ||
        Build.PRODUCT.contains("sdk") ||
        Build.PRODUCT.contains("emulator") ||
        Build.HARDWARE.contains("goldfish") ||
        Build.HARDWARE.contains("ranchu")
}
