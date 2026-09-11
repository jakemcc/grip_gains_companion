package app.grip_gains_companion.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/**
 * Generates audio tones for target weight alerts
 */
object ToneGenerator {
    
    private const val SAMPLE_RATE = 44100
    
    /**
     * Play a warning tone (medium pitch) for general off-target alert
     */
    fun playWarningTone() {
        playTone(frequency = 880.0, durationMs = 150) // A5
    }
    
    /**
     * Play a high tone indicating weight is too heavy
     */
    fun playHighTone() {
        playTone(frequency = 1320.0, durationMs = 150) // E6
    }
    
    /**
     * Play a low tone indicating weight is too light
     */
    fun playLowTone() {
        playTone(frequency = 440.0, durationMs = 150) // A4
    }

    /**
     * Play a confirmation tone when weight returns to target range
     */
    fun playOnTargetTone() {
        playTone(frequency = 660.0, durationMs = 120) // E5
    }

    /**
     * Play a timer countdown tone with distinct cues near the end.
     */
    fun playCountdownTone(second: Int) {
        when (second) {
            0 -> playTone(frequency = 1320.0, durationMs = 300) // E6
            1 -> playTone(frequency = 1174.66, durationMs = 240) // D6
            2 -> playTone(frequency = 660.0, durationMs = 180) // E5
            else -> playTone(frequency = 880.0, durationMs = 120) // A5
        }
    }
    
    /**
     * Generate and play a sine wave tone
     */
    private fun playTone(frequency: Double, durationMs: Int) {
        Thread {
            try {
                val numSamples = (SAMPLE_RATE * durationMs / 1000)
                val samples = ShortArray(numSamples)
                val amplitude = 0.3 * Short.MAX_VALUE
                
                // Envelope attack/release time in samples
                val envelopeSamples = (SAMPLE_RATE * 0.01).toInt() // 10ms
                
                for (i in 0 until numSamples) {
                    val phase = 2.0 * PI * frequency * i / SAMPLE_RATE
                    
                    // Apply envelope to avoid clicks
                    val envelope = when {
                        i < envelopeSamples -> i.toDouble() / envelopeSamples
                        i > numSamples - envelopeSamples -> (numSamples - i).toDouble() / envelopeSamples
                        else -> 1.0
                    }
                    
                    samples[i] = (sin(phase) * envelope * amplitude).toInt().toShort()
                }
                
                val bufferSize = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                
                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(maxOf(bufferSize, samples.size * 2))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                
                audioTrack.play()
                audioTrack.write(samples, 0, samples.size)
                
                // Wait for playback to complete
                Thread.sleep(durationMs.toLong() + 50)
                
                audioTrack.stop()
                audioTrack.release()
            } catch (e: Exception) {
                // Ignore audio errors
            }
        }.start()
    }
}
