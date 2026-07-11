package me.xdan.aperture.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
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
            combine(
                repository.getAllMedia(),
                repository.getAllProgress()
            ) { mediaList, progressList ->
                if (mediaList.isEmpty()) {
                    HomeState.Empty
                } else {
                    val progressMap = progressList.associateBy { it.mediaId }
                    
                    val continueWatching = mediaList.filter { media ->
                        val p = progressMap[media.id]
                        p != null && p.position > 0 && p.position < (p.duration * 0.95)
                    }.sortedByDescending { progressMap[it.id]?.lastUpdated ?: 0L }

                    HomeState.Success(
                        featured = mediaList.take(5).shuffled(),
                        rows = buildList {
                            if (continueWatching.isNotEmpty()) {
                                add(HomeRow("Continue Watching", continueWatching))
                            }
                            add(HomeRow("Recently Added", mediaList))
                            add(HomeRow("Movies", mediaList.filter { it.type == "MOVIE" }))
                            add(HomeRow("TV Shows", mediaList.filter { it.type == "EPISODE" }))
                        },
                        progressMap = progressList.associate { 
                            it.mediaId to (if (it.duration > 0) it.position.toFloat() / it.duration else 0f)
                        }
                    )
                }
            }.collectLatest {
                _homeState.value = it
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
        val rows: List<HomeRow>,
        val progressMap: Map<Long, Float> = emptyMap()
    ) : HomeState
}

data class HomeRow(
    val title: String,
    val items: List<MediaEntity>
)
