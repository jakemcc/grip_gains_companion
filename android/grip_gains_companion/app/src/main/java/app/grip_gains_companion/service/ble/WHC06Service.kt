package app.grip_gains_companion.service.ble

import android.bluetooth.le.ScanResult
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Protocol handler for Weiheng WH-C06 hanging scale
 * Uses advertisement-based protocol (no GATT connection)
 */
class WHC06Service {

    companion object {
        private const val TAG = "WHC06Service"
        private const val DISCONNECT_TIMEOUT_MS = 15000L  // 15 seconds without data = disconnected
    }

    /** Callback for force samples (weight in kg, timestamp in microseconds) */
    var onForceSample: ((Double, Long) -> Unit)? = null

    /** Callback when device stops advertising (disconnect) */
    var onDisconnect: (() -> Unit)? = null
    var assumeHardwareIsLbs: Boolean = false

    private var disconnectTimer: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())

    /**
     * Start the service
     */
    fun start() {
        Log.i(TAG, "Starting WHC06 service...")
        resetDisconnectTimer()
    }

    /**
     * Stop the service
     */
    fun stop() {
        Log.i(TAG, "Stopping WHC06 service...")
        cancelDisconnectTimer()
    }

    /**
     * Process advertisement data from WHC06
     * Called each time we receive an advertisement from the device
     */
    fun processAdvertisement(scanResult: ScanResult) {
        val rawBytes = scanResult.scanRecord?.bytes ?: return
        val weight = parseRawBytes(rawBytes) ?: return

        // Reset disconnect timer since we received data
        resetDisconnectTimer()

        // Generate synthetic timestamp
        val timestamp = generateTimestamp()

        // Send every sample - Android advertisement rate is already slow for WHC06,
        // and we need continuous samples for calibration to complete
        onForceSample?.invoke(weight, timestamp)
    }

    private fun parseRawBytes(data: ByteArray): Double? {
        var i = 0
        while (i < data.size - 1) {
            val length = data[i].toInt() and 0xFF
            if (length == 0) break

            val type = data[i + 1].toInt() and 0xFF
            if (type == 0xFF && length >= 15) {
                val dataStart = i + 2
                val highByte = data[dataStart + 12].toInt() and 0xFF
                val lowByte = data[dataStart + 13].toInt() and 0xFF

                val rawWeight = (highByte shl 8) or lowByte
                val rawValue = rawWeight.toDouble() / 100.0

                return if (assumeHardwareIsLbs) {
                    rawValue / 2.20462
                } else {
                    rawValue
                }
            }
            i += length + 1
        }
        return null
    }

    /**
     * Generate synthetic timestamp (microseconds since start)
     */
    private fun generateTimestamp(): Long {
        // WHC06 advertises at ~6.5Hz at relatively ideal conditions, but can decrease (e.g. if other bluetooth devices are present)
        // This variation causes synthetic timestamps to be inaccurate
        // Generally slow enough to just use system millis instead
        return System.currentTimeMillis()
    }

    /**
     * Reset the disconnect timer
     */
    private fun resetDisconnectTimer() {
        cancelDisconnectTimer()
        disconnectTimer = Runnable {
            Log.i(TAG, "WHC06 disconnect timeout - no advertisements received")
            onDisconnect?.invoke()
        }
        handler.postDelayed(disconnectTimer!!, DISCONNECT_TIMEOUT_MS)
    }

    /**
     * Cancel the disconnect timer
     */
    private fun cancelDisconnectTimer() {
        disconnectTimer?.let { handler.removeCallbacks(it) }
        disconnectTimer = null
    }
}
