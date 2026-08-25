package app.grip_gains_companion.service.ble

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

internal suspend fun Flow<Boolean>.collectPeriodicScanResets(
    resetPeriodMs: Long,
    resetScan: () -> Unit
) {
    distinctUntilChanged().collectLatest { eligible ->
        if (!eligible) return@collectLatest

        while (true) {
            delay(resetPeriodMs)
            resetScan()
        }
    }
}
