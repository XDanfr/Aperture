package me.xdan.aperture.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.xdan.aperture.domain.repository.MediaRepository
import me.xdan.aperture.domain.repository.LibraryPreparationStage
import me.xdan.aperture.domain.repository.UserPreferencesRepository
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val mediaRepository: MediaRepository
) : ViewModel() {

    private var preparationJob: Job? = null
    private var hasStartedPreparation = false

    // Using Boolean? where null means "loading"
    val isOnboardingCompleted: StateFlow<Boolean?> = userPreferencesRepository.isOnboardingCompleted
        .map { it as Boolean? }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val libraryPreparation = mediaRepository.preparationProgress

    init {
        viewModelScope.launch {
            userPreferencesRepository.isOnboardingCompleted
                .filter { it }
                .first()
            startLibraryPreparation()
        }
    }

    fun startLibraryPreparation() {
        if (preparationJob?.isActive == true) return
        if (
            hasStartedPreparation &&
            mediaRepository.preparationProgress.value.stage != LibraryPreparationStage.ERROR
        ) return
        hasStartedPreparation = true
        preparationJob = viewModelScope.launch {
            mediaRepository.scanLocalFiles()
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            userPreferencesRepository.setOnboardingCompleted(true)
        }
    }
}
