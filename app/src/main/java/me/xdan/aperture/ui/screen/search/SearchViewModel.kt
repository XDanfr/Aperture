package me.xdan.aperture.ui.screen.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.domain.repository.MediaRepository
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: MediaRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _results = MutableStateFlow<List<MediaEntity>>(emptyList())
    val results: StateFlow<List<MediaEntity>> = _results

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
        viewModelScope.launch {
            repository.searchMedia(newQuery).collectLatest {
                _results.value = it
            }
        }
    }
}
