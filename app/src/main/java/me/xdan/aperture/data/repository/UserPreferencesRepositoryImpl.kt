package me.xdan.aperture.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.xdan.aperture.domain.repository.UserPreferencesRepository
import javax.inject.Inject

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

class UserPreferencesRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : UserPreferencesRepository {

    private val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    private val HIDE_FINISHED_FROM_SPOTLIGHT = booleanPreferencesKey("hide_finished_from_spotlight")
    private val FINISHED_SPOTLIGHT_EXCLUSION_DAYS = intPreferencesKey("finished_spotlight_exclusion_days")
    private val THEME_ID = stringPreferencesKey("theme_id")
    private val TUTORIAL_REQUIRED = booleanPreferencesKey("tutorial_required")

    override val isOnboardingCompleted: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[ONBOARDING_COMPLETED] ?: false
        }

    override val hideFinishedFromSpotlight: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[HIDE_FINISHED_FROM_SPOTLIGHT] ?: true }

    override val finishedSpotlightExclusionDays: Flow<Int> = context.dataStore.data
        .map { preferences -> preferences[FINISHED_SPOTLIGHT_EXCLUSION_DAYS] ?: 14 }

    override val themeId: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[THEME_ID] ?: "purple" }

    override val isTutorialRequired: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[TUTORIAL_REQUIRED] ?: false }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ONBOARDING_COMPLETED] = completed
        }
    }

    override suspend fun setHideFinishedFromSpotlight(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[HIDE_FINISHED_FROM_SPOTLIGHT] = enabled
        }
    }

    override suspend fun setFinishedSpotlightExclusionDays(days: Int) {
        context.dataStore.edit { preferences ->
            preferences[FINISHED_SPOTLIGHT_EXCLUSION_DAYS] = days.coerceIn(1, 365)
        }
    }

    override suspend fun setThemeId(themeId: String) {
        context.dataStore.edit { preferences -> preferences[THEME_ID] = themeId }
    }

    override suspend fun setTutorialRequired(required: Boolean) {
        context.dataStore.edit { preferences -> preferences[TUTORIAL_REQUIRED] = required }
    }
}
