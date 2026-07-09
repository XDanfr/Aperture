package me.xdan.aperture.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import me.xdan.aperture.domain.repository.MediaRepository
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: MediaRepository
) : ViewModel() {

    fun forceRescan() {
        viewModelScope.launch {
            repository.scanLocalFiles()
        }
    }

    fun clearCache() {
        // Implement cache clearing logic if necessary (e.g. Coil cache)
    }
}
