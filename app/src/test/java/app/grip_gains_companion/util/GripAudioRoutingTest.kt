package app.grip_gains_companion.util

import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class GripAudioRoutingTest {

    @Test
    fun gripMutingTargetsUnrelatedMediaAudio() {
        assertEquals(AudioManager.STREAM_MUSIC, GripAudioRouting.MUTED_PHONE_AUDIO_STREAM)
    }

    @Test
    fun targetGuidanceUsesAnAudibleStreamSeparateFromMutedMedia() {
        assertEquals(AudioManager.STREAM_ALARM, GripAudioRouting.TARGET_GUIDANCE_STREAM)
        assertNotEquals(
            GripAudioRouting.MUTED_PHONE_AUDIO_STREAM,
            GripAudioRouting.TARGET_GUIDANCE_STREAM
        )
    }
}
