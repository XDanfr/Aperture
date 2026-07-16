package me.xdan.aperture.domain.repository

import kotlinx.coroutines.flow.Flow
import me.xdan.aperture.domain.model.AmbientBrandPlacement
import me.xdan.aperture.domain.model.AmbientModeType

interface UserPreferencesRepository {
    val isOnboardingCompleted: Flow<Boolean>
    val hideFinishedFromSpotlight: Flow<Boolean>
    val finishedSpotlightExclusionDays: Flow<Int>
    val roundedSpotlight: Flow<Boolean>
    val themeId: Flow<String>
    val isTutorialRequired: Flow<Boolean>
    val showPresentationMode: Flow<String>
    val subtitleTextScale: Flow<Float>
    val subtitleColour: Flow<String>
    val subtitleBackgroundOpacity: Flow<Float>
    val ambientMode: Flow<AmbientModeType>
    val ambientWallBrandPlacement: Flow<AmbientBrandPlacement>
    val ambientShowClock: Flow<Boolean>

    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun setHideFinishedFromSpotlight(enabled: Boolean)
    suspend fun setFinishedSpotlightExclusionDays(days: Int)
    suspend fun setRoundedSpotlight(enabled: Boolean)
    suspend fun setThemeId(themeId: String)
    suspend fun setTutorialRequired(required: Boolean)
    suspend fun setShowPresentationMode(mode: String)
    suspend fun setSubtitleTextScale(scale: Float)
    suspend fun setSubtitleColour(colour: String)
    suspend fun setSubtitleBackgroundOpacity(opacity: Float)
    suspend fun setAmbientMode(mode: AmbientModeType)
    suspend fun setAmbientWallBrandPlacement(placement: AmbientBrandPlacement)
    suspend fun setAmbientShowClock(enabled: Boolean)
}
