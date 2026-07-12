package me.xdan.aperture.ui.screen.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import me.xdan.aperture.domain.repository.MediaRepository
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    repository: MediaRepository
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    val results = _query.flatMapLatest { query ->
        if (query.isBlank()) flowOf(emptyList()) else repository.searchMedia(query)
    }.map { matches ->
        val movies = matches.filter { it.type != "EPISODE" }
        val shows = matches.filter { it.type == "EPISODE" }
            .groupBy { it.title }
            .values
            .mapNotNull { episodes ->
                episodes.firstOrNull { !it.posterPath.isNullOrBlank() } ?: episodes.firstOrNull()
            }
        (movies + shows).sortedBy { it.title.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }
}
