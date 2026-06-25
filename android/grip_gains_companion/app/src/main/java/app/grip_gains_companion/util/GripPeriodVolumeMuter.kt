package app.grip_gains_companion.util

import android.content.Context
import android.media.AudioManager

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
    private val volumeAccess: GripPeriodVolumeAccess
) {
    private var volumeBeforeMute: Int? = null

    fun onGripActiveChanged(isGripActive: Boolean, enabled: Boolean) {
        if (!enabled || !isGripActive) {
            restoreIfNeeded()
            return
        }

        if (volumeBeforeMute == null) {
            volumeBeforeMute = volumeAccess.currentVolume
            volumeAccess.mute()
        }
    }

    fun restoreIfNeeded() {
        val volume = volumeBeforeMute ?: return
        volumeBeforeMute = null
        volumeAccess.restore(volume)
    }
}
