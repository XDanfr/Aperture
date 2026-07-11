package me.xdan.aperture.domain.repository

import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    val isOnboardingCompleted: Flow<Boolean>
    val hideFinishedFromSpotlight: Flow<Boolean>
    val finishedSpotlightExclusionDays: Flow<Int>

    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun setHideFinishedFromSpotlight(enabled: Boolean)
    suspend fun setFinishedSpotlightExclusionDays(days: Int)
}
