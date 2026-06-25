package app.grip_gains_companion.util

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock

interface GripPeriodVolumeAccess {
    val currentVolume: Int
    fun mute()
    fun restore(volume: Int)
}

class AudioManagerGripPeriodVolumeAccess(context: Context) : GripPeriodVolumeAccess {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    override val currentVolume: Int
        get() = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

    override fun mute() {
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
    }

    override fun restore(volume: Int) {
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
    }
}

class GripPeriodVolumeMuter(
    private val volumeAccess: GripPeriodVolumeAccess,
    private val nowMillis: () -> Long = SystemClock::elapsedRealtime
) {
    private var gripStartedAtMillis: Long? = null
    private var volumeBeforeMute: Int? = null

    fun onGripActiveChanged(isGripActive: Boolean, enabled: Boolean) {
        if (!enabled || !isGripActive) {
            gripStartedAtMillis = null
            restoreIfNeeded()
            return
        }

        val startedAt = gripStartedAtMillis ?: nowMillis().also {
            gripStartedAtMillis = it
        }
        if (volumeBeforeMute == null && nowMillis() - startedAt >= MUTE_DELAY_MS) {
            volumeBeforeMute = volumeAccess.currentVolume
            volumeAccess.mute()
        }
    }

    fun restoreIfNeeded() {
        val volume = volumeBeforeMute ?: return
        volumeBeforeMute = null
        volumeAccess.restore(volume)
    }

    private companion object {
        const val MUTE_DELAY_MS = 4_000L
    }
}
