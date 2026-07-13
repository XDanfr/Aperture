package me.xdan.aperture.domain.repository

import kotlinx.coroutines.flow.Flow

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
}
