@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package me.xdan.aperture.ui.screen.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import kotlinx.coroutines.flow.first
import me.xdan.aperture.domain.repository.UserPreferencesRepository

class PlayerEngineFactory constructor(
    private val context: Context,
    private val preferences: UserPreferencesRepository
) {
    suspend fun createExoPlayerEngine(
        forceSoftware: Boolean = false,
        forceNoTunneling: Boolean = false,
        force8Bit: Boolean = false
    ): ExoPlayerEngine {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val passthroughLossless = preferences.losslessPassthrough.first()
        val renderersFactory = AudioRoutingRenderersFactory(context, passthroughLossless)
        
        val softwareVideo = if (forceSoftware) true else preferences.softwareVideoDecoding.first()
        val tunneling = if (forceNoTunneling) false else preferences.tunnelingEnabled.first()
        
        renderersFactory
            .setExtensionRendererMode(
                if (softwareVideo) DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON 
                else DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
            )
            .setEnableDecoderFallback(softwareVideo)

        // Force 8-bit output in software mode if requested. 
        // This is done by configuring the renderer to avoid 10-bit YUV profiles.
        if (force8Bit && softwareVideo) {
            // Placeholder for NextLib configuration if available. 
            // In standard ExoPlayer FFmpeg, this often requires rebuilding 
            // the extension or using specialized surface shaders.
        }

        val trackSelector = DefaultTrackSelector(context)
        trackSelector.parameters = trackSelector.buildUponParameters()
            .setTunnelingEnabled(tunneling)
            .build()
        
        val player = ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            
        return ExoPlayerEngine(player)
    }
}

/**
 * Plays every audio format on any TV. DTS and Dolby TrueHD go to the FFmpeg decoder (unless
 * [passthroughLossless]): many TVs list DTS in their HDMI capabilities but play nothing (the
 * Chromecast with Google TV played a moment of DTS-HD and then silence), and Android has no
 * decoder of its own for either. AC-3 and E-AC-3 (including Atmos) still pass through when the
 * TV takes them. The FFmpeg audio renderer is always there, as the fallback.
 */
private class AudioRoutingRenderersFactory(
    context: Context,
    private val passthroughLossless: Boolean
) : NextRenderersFactory(context) {
    override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: androidx.media3.exoplayer.mediacodec.MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: androidx.media3.exoplayer.audio.AudioSink,
        eventHandler: android.os.Handler,
        eventListener: androidx.media3.exoplayer.audio.AudioRendererEventListener,
        out: java.util.ArrayList<androidx.media3.exoplayer.Renderer>
    ) {
        // Extension mode follows the software video setting; audio always wants FFmpeg behind
        // the platform decoders.
        val audioMode = if (extensionRendererMode == EXTENSION_RENDERER_MODE_OFF) EXTENSION_RENDERER_MODE_ON else extensionRendererMode
        super.buildAudioRenderers(context, audioMode, mediaCodecSelector, enableDecoderFallback, audioSink, eventHandler, eventListener, out)
    }

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): androidx.media3.exoplayer.audio.AudioSink? {
        val sink = super.buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams) ?: return null
        if (passthroughLossless) return sink
        // A wrapper rather than fixed capabilities: the sink refreshes its own from HDMI events,
        // which would bring DTS back.
        return object : androidx.media3.exoplayer.audio.ForwardingAudioSink(sink) {
            override fun supportsFormat(format: androidx.media3.common.Format) =
                format.sampleMimeType !in DECODED_HERE && super.supportsFormat(format)

            override fun getFormatSupport(format: androidx.media3.common.Format) =
                if (format.sampleMimeType in DECODED_HERE) androidx.media3.exoplayer.audio.AudioSink.SINK_FORMAT_UNSUPPORTED
                else super.getFormatSupport(format)
        }
    }

    private companion object {
        /** Never passed through: the FFmpeg renderer decodes these to PCM. */
        val DECODED_HERE = setOf(
            androidx.media3.common.MimeTypes.AUDIO_DTS,
            androidx.media3.common.MimeTypes.AUDIO_DTS_HD,
            androidx.media3.common.MimeTypes.AUDIO_DTS_EXPRESS,
            androidx.media3.common.MimeTypes.AUDIO_DTS_X,
            androidx.media3.common.MimeTypes.AUDIO_TRUEHD
        )
    }
}
