package me.xdan.aperture.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
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
    private val ROUNDED_SPOTLIGHT = booleanPreferencesKey("rounded_spotlight")
    private val THEME_ID = stringPreferencesKey("theme_id")
    private val TUTORIAL_REQUIRED = booleanPreferencesKey("tutorial_required")
    private val SHOW_PRESENTATION_MODE = stringPreferencesKey("show_presentation_mode")
    private val SUBTITLE_TEXT_SCALE = floatPreferencesKey("subtitle_text_scale")
    private val SUBTITLE_COLOUR = stringPreferencesKey("subtitle_colour")
    private val SUBTITLE_BACKGROUND_OPACITY = floatPreferencesKey("subtitle_background_opacity")

    override val isOnboardingCompleted: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[ONBOARDING_COMPLETED] ?: false
        }

    override val hideFinishedFromSpotlight: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[HIDE_FINISHED_FROM_SPOTLIGHT] ?: true }

    override val finishedSpotlightExclusionDays: Flow<Int> = context.dataStore.data
        .map { preferences -> preferences[FINISHED_SPOTLIGHT_EXCLUSION_DAYS] ?: 14 }

    override val roundedSpotlight: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[ROUNDED_SPOTLIGHT] ?: true }

    override val themeId: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[THEME_ID] ?: "purple" }

    override val isTutorialRequired: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[TUTORIAL_REQUIRED] ?: false }

    override val showPresentationMode: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[SHOW_PRESENTATION_MODE] ?: "grouped" }

    override val subtitleTextScale: Flow<Float> = context.dataStore.data
        .map { preferences -> preferences[SUBTITLE_TEXT_SCALE] ?: 1f }

    override val subtitleColour: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[SUBTITLE_COLOUR] ?: "white" }

    override val subtitleBackgroundOpacity: Flow<Float> = context.dataStore.data
        .map { preferences -> preferences[SUBTITLE_BACKGROUND_OPACITY] ?: 0.55f }

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

    override suspend fun setRoundedSpotlight(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[ROUNDED_SPOTLIGHT] = enabled }
    }

    override suspend fun setThemeId(themeId: String) {
        context.dataStore.edit { preferences -> preferences[THEME_ID] = themeId }
    }

    override suspend fun setTutorialRequired(required: Boolean) {
        context.dataStore.edit { preferences -> preferences[TUTORIAL_REQUIRED] = required }
    }

    override suspend fun setShowPresentationMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[SHOW_PRESENTATION_MODE] = if (mode == "episodes") "episodes" else "grouped"
        }
    }

    override suspend fun setSubtitleTextScale(scale: Float) {
        context.dataStore.edit { preferences -> preferences[SUBTITLE_TEXT_SCALE] = scale.coerceIn(0.7f, 1.6f) }
    }

    override suspend fun setSubtitleColour(colour: String) {
        context.dataStore.edit { preferences -> preferences[SUBTITLE_COLOUR] = colour }
    }

    override suspend fun setSubtitleBackgroundOpacity(opacity: Float) {
        context.dataStore.edit { preferences ->
            preferences[SUBTITLE_BACKGROUND_OPACITY] = opacity.coerceIn(0f, 0.9f)
        }
    }
}
