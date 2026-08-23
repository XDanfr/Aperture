@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package me.xdan.aperture.ui.screen.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import kotlinx.coroutines.flow.first
import me.xdan.aperture.domain.repository.UserPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerEngineFactory @Inject constructor(
    @ApplicationContext private val context: Context,
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

        val renderersFactory = NextRenderersFactory(context)
        
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
