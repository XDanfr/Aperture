package me.xdan.aperture.ui.screen.home

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
class HomeViewModel @Inject constructor(
    private val repository: MediaRepository
) : ViewModel() {

    private val _homeState = MutableStateFlow<HomeState>(HomeState.Loading)
    val homeState: StateFlow<HomeState> = _homeState

    init {
        viewModelScope.launch {
            repository.scanLocalFiles()
            repository.getAllMedia().collectLatest { mediaList ->
                if (mediaList.isEmpty()) {
                    _homeState.value = HomeState.Empty
                } else {
                    _homeState.value = HomeState.Success(
                        featured = mediaList.take(5).shuffled(),
                        rows = listOf(
                            HomeRow("Continue Watching", mediaList.shuffled()),
                            HomeRow("Recently Added", mediaList),
                            HomeRow("Movies", mediaList.filter { it.type == "MOVIE" }.shuffled()),
                            HomeRow("TV Shows", mediaList.filter { it.type == "EPISODE" }.shuffled())
                        )
                    )
                }
            }
        }
    }

    fun softRefresh() {
        val currentState = _homeState.value
        if (currentState is HomeState.Success) {
            _homeState.value = currentState.copy(
                rows = currentState.rows.map { row ->
                    row.copy(items = row.items.shuffled())
                }
            )
        }
    }
}

sealed interface HomeState {
    data object Loading : HomeState
    data object Empty : HomeState
    data class Success(
        val featured: List<MediaEntity>,
        val rows: List<HomeRow>
    ) : HomeState
}

data class HomeRow(
    val title: String,
    val items: List<MediaEntity>
)
