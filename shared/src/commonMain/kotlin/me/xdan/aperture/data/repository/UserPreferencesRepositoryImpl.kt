package me.xdan.aperture.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.xdan.aperture.domain.repository.UserPreferencesRepository
import me.xdan.aperture.domain.model.AmbientBrandPlacement
import me.xdan.aperture.domain.model.AmbientModeType

/** Backed by the `user_prefs` Preferences DataStore each platform creates. */
class UserPreferencesRepositoryImpl(
    private val dataStore: DataStore<Preferences>
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
    private val AMBIENT_MODE = stringPreferencesKey("ambient_mode")
    private val AMBIENT_WALL_BRAND_PLACEMENT = stringPreferencesKey("ambient_wall_brand_placement")
    private val AMBIENT_SHOW_CLOCK = booleanPreferencesKey("ambient_show_clock")
    private val CLASSIC_PLAYER_CONTROLS = booleanPreferencesKey("classic_player_controls")
    private val SHOULD_SHOW_COMPATIBILITY_WARNING = booleanPreferencesKey("should_show_compatibility_warning")
    private val TUNNELING_ENABLED = booleanPreferencesKey("tunneling_enabled")
    private val SOFTWARE_VIDEO_DECODING = booleanPreferencesKey("software_video_decoding")
    private val PLAYBACK_ENGINE = stringPreferencesKey("playback_engine")

    override val isOnboardingCompleted: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[ONBOARDING_COMPLETED] ?: false
        }

    override val hideFinishedFromSpotlight: Flow<Boolean> = dataStore.data
        .map { preferences -> preferences[HIDE_FINISHED_FROM_SPOTLIGHT] ?: true }

    override val finishedSpotlightExclusionDays: Flow<Int> = dataStore.data
        .map { preferences -> preferences[FINISHED_SPOTLIGHT_EXCLUSION_DAYS] ?: 14 }

    override val roundedSpotlight: Flow<Boolean> = dataStore.data
        .map { preferences -> preferences[ROUNDED_SPOTLIGHT] ?: true }

    override val themeId: Flow<String> = dataStore.data
        .map { preferences -> preferences[THEME_ID] ?: "purple" }

    override val isTutorialRequired: Flow<Boolean> = dataStore.data
        .map { preferences -> preferences[TUTORIAL_REQUIRED] ?: false }

    override val showPresentationMode: Flow<String> = dataStore.data
        .map { preferences -> preferences[SHOW_PRESENTATION_MODE] ?: "grouped" }

    override val subtitleTextScale: Flow<Float> = dataStore.data
        .map { preferences -> preferences[SUBTITLE_TEXT_SCALE] ?: 1f }

    override val subtitleColour: Flow<String> = dataStore.data
        .map { preferences -> preferences[SUBTITLE_COLOUR] ?: "white" }

    override val subtitleBackgroundOpacity: Flow<Float> = dataStore.data
        .map { preferences -> preferences[SUBTITLE_BACKGROUND_OPACITY] ?: 0.55f }

    override val ambientMode: Flow<AmbientModeType> = dataStore.data
        .map { preferences -> AmbientModeType.fromPreference(preferences[AMBIENT_MODE]) }

    override val ambientWallBrandPlacement: Flow<AmbientBrandPlacement> = dataStore.data
        .map { preferences ->
            AmbientBrandPlacement.fromPreference(preferences[AMBIENT_WALL_BRAND_PLACEMENT])
        }

    override val ambientShowClock: Flow<Boolean> = dataStore.data
        .map { preferences -> preferences[AMBIENT_SHOW_CLOCK] ?: false }

    override val classicPlayerControls: Flow<Boolean> = dataStore.data
        .map { preferences -> preferences[CLASSIC_PLAYER_CONTROLS] ?: false }

    override val shouldShowCompatibilityWarning: Flow<Boolean> = dataStore.data
        .map { preferences -> preferences[SHOULD_SHOW_COMPATIBILITY_WARNING] ?: true }

    override val tunnelingEnabled: Flow<Boolean> = dataStore.data
        .map { it[TUNNELING_ENABLED] ?: true }

    override val softwareVideoDecoding: Flow<Boolean> = dataStore.data
        .map { it[SOFTWARE_VIDEO_DECODING] ?: true }

    override val playbackEngine: Flow<String> = dataStore.data
        .map { it[PLAYBACK_ENGINE] ?: "auto" }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { preferences ->
            preferences[ONBOARDING_COMPLETED] = completed
        }
    }

    override suspend fun setHideFinishedFromSpotlight(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[HIDE_FINISHED_FROM_SPOTLIGHT] = enabled
        }
    }

    override suspend fun setFinishedSpotlightExclusionDays(days: Int) {
        dataStore.edit { preferences ->
            preferences[FINISHED_SPOTLIGHT_EXCLUSION_DAYS] = days.coerceIn(1, 365)
        }
    }

    override suspend fun setRoundedSpotlight(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[ROUNDED_SPOTLIGHT] = enabled }
    }

    override suspend fun setThemeId(themeId: String) {
        dataStore.edit { preferences -> preferences[THEME_ID] = themeId }
    }

    override suspend fun setTutorialRequired(required: Boolean) {
        dataStore.edit { preferences -> preferences[TUTORIAL_REQUIRED] = required }
    }

    override suspend fun setShowPresentationMode(mode: String) {
        dataStore.edit { preferences ->
            preferences[SHOW_PRESENTATION_MODE] = if (mode == "episodes") "episodes" else "grouped"
        }
    }

    override suspend fun setSubtitleTextScale(scale: Float) {
        dataStore.edit { preferences -> preferences[SUBTITLE_TEXT_SCALE] = scale.coerceIn(0.7f, 1.6f) }
    }

    override suspend fun setSubtitleColour(colour: String) {
        dataStore.edit { preferences -> preferences[SUBTITLE_COLOUR] = colour }
    }

    override suspend fun setSubtitleBackgroundOpacity(opacity: Float) {
        dataStore.edit { preferences ->
            preferences[SUBTITLE_BACKGROUND_OPACITY] = opacity.coerceIn(0f, 0.9f)
        }
    }

    override suspend fun setAmbientMode(mode: AmbientModeType) {
        dataStore.edit { preferences -> preferences[AMBIENT_MODE] = mode.preferenceValue }
    }

    override suspend fun setAmbientWallBrandPlacement(placement: AmbientBrandPlacement) {
        dataStore.edit { preferences ->
            preferences[AMBIENT_WALL_BRAND_PLACEMENT] = placement.preferenceValue
        }
    }

    override suspend fun setAmbientShowClock(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[AMBIENT_SHOW_CLOCK] = enabled }
    }

    override suspend fun setClassicPlayerControls(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[CLASSIC_PLAYER_CONTROLS] = enabled }
    }

    override suspend fun setShouldShowCompatibilityWarning(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[SHOULD_SHOW_COMPATIBILITY_WARNING] = enabled }
    }

    override suspend fun setTunnelingEnabled(enabled: Boolean) {
        dataStore.edit { it[TUNNELING_ENABLED] = enabled }
    }

    override suspend fun setSoftwareVideoDecoding(enabled: Boolean) {
        dataStore.edit { it[SOFTWARE_VIDEO_DECODING] = enabled }
    }

    override suspend fun setPlaybackEngine(engine: String) {
        dataStore.edit { it[PLAYBACK_ENGINE] = engine }
    }
}
