package me.xdan.aperture.ui.screen.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import me.xdan.aperture.domain.repository.MediaRepository
import me.xdan.aperture.domain.repository.UserPreferencesRepository
import me.xdan.aperture.data.local.entity.MediaEntity
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    repository: MediaRepository,
    preferences: UserPreferencesRepository
) : ViewModel() {
    val movies = repository.getMediaByType("MOVIE")
        .map { items -> items.sortedBy { it.title.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val shows = repository.getMediaByType("EPISODE")
        .map { items ->
            items.sortedWith(
                compareBy(
                    { it.title.lowercase() },
                    { it.seasonNumber ?: Int.MAX_VALUE },
                    { it.episodeNumber ?: Int.MAX_VALUE }
                )
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val showPresentationMode = preferences.showPresentationMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "grouped")
}

data class ShowGroup(
    val title: String,
    val episodes: List<MediaEntity>
) {
    val representative: MediaEntity
        get() = episodes.firstOrNull { !it.posterPath.isNullOrBlank() } ?: episodes.first()
}
