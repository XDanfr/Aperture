package me.xdan.aperture.ui.screen.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
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
    private val content = LibraryContent(
        repository.getMediaByType("MOVIE"),
        repository.getMediaByType("EPISODE"),
        viewModelScope
    )
    val movies = content.movies
    val showGroups = content.showGroups

    val showPresentationMode = preferences.showPresentationMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "grouped")
}

data class ShowGroup(
    val title: String,
    val episodes: List<MediaEntity>
) {
    val representative: MediaEntity =
        // a show card must open at the first available episode, not whichever episode happened to receive poster art first.
        episodes.minWithOrNull(
            compareBy<MediaEntity>(
                { it.seasonNumber ?: Int.MAX_VALUE },
                { it.episodeNumber ?: Int.MAX_VALUE },
                { it.id }
            )
        ) ?: episodes.first()

    val seasons: List<ShowSeason> = episodes.groupBy { it.seasonNumber ?: 0 }
        .toSortedMap()
        .map { (number, episodes) -> ShowSeason(number, episodes) }
}

data class ShowSeason(val number: Int, val episodes: List<MediaEntity>)
