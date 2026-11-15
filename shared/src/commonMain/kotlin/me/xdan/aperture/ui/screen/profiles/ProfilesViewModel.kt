package me.xdan.aperture.ui.screen.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.xdan.aperture.data.local.entity.ProfileEntity
import me.xdan.aperture.domain.repository.ProfileRepository

class ProfilesViewModel(private val repository: ProfileRepository) : ViewModel() {
    val profiles: StateFlow<List<ProfileEntity>> = repository.profiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeProfileId: StateFlow<Long> = repository.activeProfileId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileEntity.DEFAULT_ID)

    fun select(profileId: Long) {
        viewModelScope.launch { repository.selectProfile(profileId) }
    }

    fun create(name: String, colorId: String, switchTo: Boolean = false) {
        viewModelScope.launch {
            val id = repository.createProfile(name, colorId)
            if (switchTo) repository.selectProfile(id)
        }
    }

    fun update(profileId: Long, name: String, colorId: String) {
        viewModelScope.launch { repository.updateProfile(profileId, name, colorId) }
    }

    fun delete(profileId: Long) {
        viewModelScope.launch { repository.deleteProfile(profileId) }
    }
}
