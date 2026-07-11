package me.xdan.aperture.ui.screen.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import me.xdan.aperture.domain.repository.MediaRepository
import me.xdan.aperture.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val spotlightSettings: StateFlow<SpotlightSettings> = combine(
        userPreferencesRepository.hideFinishedFromSpotlight,
        userPreferencesRepository.finishedSpotlightExclusionDays
    ) { hideFinished, exclusionDays ->
        SpotlightSettings(hideFinished, exclusionDays)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SpotlightSettings()
    )

    fun forceRescan() {
        viewModelScope.launch {
            repository.scanLocalFiles()
        }
    }

    fun clearCache() {
        context.imageLoader.memoryCache?.clear()
        context.imageLoader.diskCache?.clear()
    }

    fun setHideFinishedFromSpotlight(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setHideFinishedFromSpotlight(enabled)
        }
    }

    fun setFinishedSpotlightExclusionDays(days: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setFinishedSpotlightExclusionDays(days)
        }
    }
}

data class SpotlightSettings(
    val hideFinishedFromSpotlight: Boolean = true,
    val exclusionDays: Int = 14
)
