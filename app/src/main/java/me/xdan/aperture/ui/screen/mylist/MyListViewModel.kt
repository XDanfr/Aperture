package me.xdan.aperture.ui.screen.mylist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.domain.repository.MediaRepository
import javax.inject.Inject

@HiltViewModel
class MyListViewModel @Inject constructor(
    repository: MediaRepository
) : ViewModel() {
    val media: StateFlow<List<MediaEntity>> = repository.getFavoriteMedia()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
