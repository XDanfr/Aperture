package me.xdan.aperture.ui.screen.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import me.xdan.aperture.domain.repository.MediaRepository
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: MediaRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    fun forceRescan() {
        viewModelScope.launch {
            repository.scanLocalFiles()
        }
    }

    fun clearCache() {
        context.imageLoader.memoryCache?.clear()
        context.imageLoader.diskCache?.clear()
    }
}
