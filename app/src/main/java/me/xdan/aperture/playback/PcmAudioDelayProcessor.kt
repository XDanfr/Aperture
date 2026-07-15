@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package me.xdan.aperture.playback

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer

/**
 * Adds silence before decoded PCM audio so that it can be aligned with the picture.
 *
 * This intentionally implements delay only. Advancing audio requires delaying the video
 * renderer instead, because audio which has not been decoded cannot be played early.
 */
class PcmAudioDelayProcessor : BaseAudioProcessor() {
    @Volatile
    var delayMs: Long = 0L
        set(value) {
            field = value.coerceIn(0L, MAX_DELAY_MS)
        }

    private var pendingSilenceBytes = 0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat =
        inputAudioFormat

    override fun onFlush() {
        if (inputAudioFormat == AudioProcessor.AudioFormat.NOT_SET ||
            inputAudioFormat.sampleRate <= 0 ||
            inputAudioFormat.bytesPerFrame <= 0
        ) {
            pendingSilenceBytes = 0
            return
        }

        val frames = inputAudioFormat.sampleRate.toLong() * delayMs / 1_000L
        pendingSilenceBytes = (frames * inputAudioFormat.bytesPerFrame)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val inputBytes = inputBuffer.remaining()
        val output = replaceOutputBuffer(pendingSilenceBytes + inputBytes)

        var remainingSilence = pendingSilenceBytes
        while (remainingSilence > 0) {
            val amount = minOf(remainingSilence, ZERO_BYTES.size)
            output.put(ZERO_BYTES, 0, amount)
            remainingSilence -= amount
        }
        pendingSilenceBytes = 0

        output.put(inputBuffer)
        output.flip()
    }

    companion object {
        const val MAX_DELAY_MS = 5_000L
        private val ZERO_BYTES = ByteArray(4_096)
    }
}
