package app.grip_gains_companion.service.ble

import app.grip_gains_companion.config.AppConstants
import app.grip_gains_companion.model.DeviceType
import java.util.UUID

internal class TindeqShutdownCoordinator(
    private val postDelayed: (Runnable, Long) -> Unit,
    private val removeCallbacks: (Runnable) -> Unit
) {
    private var pendingDisconnect: (() -> Unit)? = null
    private var timeoutRunnable: Runnable? = null

    fun disconnect(
        deviceType: DeviceType?,
        sendShutdownCommand: (ByteArray) -> Boolean,
        disconnect: () -> Unit
    ): Boolean {
        if (deviceType != DeviceType.TINDEQ_PROGRESSOR) {
            disconnect()
            return false
        }

        completePending()

        val writeQueued = sendShutdownCommand(AppConstants.PROGRESSOR_SHUTDOWN_COMMAND)
        if (!writeQueued) {
            disconnect()
            return false
        }

        pendingDisconnect = disconnect
        val timeout = Runnable { completePending() }
        timeoutRunnable = timeout
        postDelayed(timeout, AppConstants.TINDEQ_SHUTDOWN_ACK_TIMEOUT_MS)
        return true
    }

    fun onWriteComplete(characteristicUuid: UUID) {
        if (characteristicUuid == AppConstants.PROGRESSOR_WRITE_CHARACTERISTIC_UUID) {
            completePending()
        }
    }

    private fun completePending() {
        val disconnect = pendingDisconnect ?: return
        pendingDisconnect = null
        timeoutRunnable?.let(removeCallbacks)
        timeoutRunnable = null
        disconnect()
    }
}
