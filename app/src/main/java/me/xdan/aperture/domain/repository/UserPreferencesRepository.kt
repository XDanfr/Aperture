package me.xdan.aperture.domain.repository

import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    val isOnboardingCompleted: Flow<Boolean>
    val hideFinishedFromSpotlight: Flow<Boolean>
    val finishedSpotlightExclusionDays: Flow<Int>
    val themeId: Flow<String>
    val isTutorialRequired: Flow<Boolean>

    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun setHideFinishedFromSpotlight(enabled: Boolean)
    suspend fun setFinishedSpotlightExclusionDays(days: Int)
    suspend fun setThemeId(themeId: String)
    suspend fun setTutorialRequired(required: Boolean)
}
