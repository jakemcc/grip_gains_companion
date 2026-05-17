package app.grip_gains_companion.service.ble

import app.grip_gains_companion.config.AppConstants
import app.grip_gains_companion.model.DeviceType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TindeqShutdownCoordinatorTest {

    @Test
    fun waitsForTindeqWriteAckBeforeDisconnecting() {
        val scheduledTimeouts = mutableListOf<Runnable>()
        val coordinator = TindeqShutdownCoordinator(
            postDelayed = { runnable, delayMs ->
                assertEquals(AppConstants.TINDEQ_SHUTDOWN_ACK_TIMEOUT_MS, delayMs)
                scheduledTimeouts += runnable
            },
            removeCallbacks = { runnable ->
                scheduledTimeouts.remove(runnable)
            }
        )

        var disconnected = false
        val started = coordinator.disconnect(
            deviceType = DeviceType.TINDEQ_PROGRESSOR,
            sendShutdownCommand = { command ->
                assertArrayEquals(byteArrayOf(0x6E.toByte()), command)
                true
            },
            disconnect = {
                disconnected = true
            }
        )

        assertTrue(started)
        assertFalse(disconnected)
        assertEquals(1, scheduledTimeouts.size)

        coordinator.onWriteComplete(AppConstants.PROGRESSOR_WRITE_CHARACTERISTIC_UUID)

        assertTrue(disconnected)
        assertTrue(scheduledTimeouts.isEmpty())
    }

    @Test
    fun timeoutDisconnectsTindeqWhenWriteAckDoesNotArrive() {
        val scheduledTimeouts = mutableListOf<Runnable>()
        val coordinator = TindeqShutdownCoordinator(
            postDelayed = { runnable, _ -> scheduledTimeouts += runnable },
            removeCallbacks = { runnable -> scheduledTimeouts.remove(runnable) }
        )

        var disconnected = false
        coordinator.disconnect(
            deviceType = DeviceType.TINDEQ_PROGRESSOR,
            sendShutdownCommand = { true },
            disconnect = { disconnected = true }
        )

        assertFalse(disconnected)

        scheduledTimeouts.single().run()

        assertTrue(disconnected)
    }

    @Test
    fun disconnectsImmediatelyWhenTindeqWriteCannotBeQueued() {
        val coordinator = TindeqShutdownCoordinator(
            postDelayed = { _, _ -> error("timeout should not be scheduled") },
            removeCallbacks = { _ -> error("nothing should be removed") }
        )

        var disconnected = false
        val started = coordinator.disconnect(
            deviceType = DeviceType.TINDEQ_PROGRESSOR,
            sendShutdownCommand = { false },
            disconnect = { disconnected = true }
        )

        assertFalse(started)
        assertTrue(disconnected)
    }

    @Test
    fun nonTindeqDevicesDisconnectWithoutShutdownCommand() {
        val coordinator = TindeqShutdownCoordinator(
            postDelayed = { _, _ -> error("timeout should not be scheduled") },
            removeCallbacks = { _ -> error("nothing should be removed") }
        )

        var disconnected = false
        val started = coordinator.disconnect(
            deviceType = DeviceType.PITCH_SIX_FORCE_BOARD,
            sendShutdownCommand = { error("shutdown command should not be sent") },
            disconnect = { disconnected = true }
        )

        assertFalse(started)
        assertTrue(disconnected)
    }
}
