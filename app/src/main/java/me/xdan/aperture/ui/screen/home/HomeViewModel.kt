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
import me.xdan.aperture.domain.repository.UserPreferencesRepository
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _homeState = MutableStateFlow<HomeState>(HomeState.Loading)
    val homeState: StateFlow<HomeState> = _homeState
    private val suggestionGeneration = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            combine(
                combine(
                    repository.getAllMedia(),
                    repository.getAllProgress()
                ) { mediaList, progressList -> mediaList to progressList },
                combine(
                    userPreferencesRepository.hideFinishedFromSpotlight,
                    userPreferencesRepository.finishedSpotlightExclusionDays
                ) { hideFinished, exclusionDays -> hideFinished to exclusionDays },
                suggestionGeneration
            ) { (mediaList, progressList), (hideFinished, exclusionDays), generation ->
                if (mediaList.isEmpty()) {
                    HomeState.Empty
                } else {
                    val progressMap = progressList.associateBy { it.mediaId }
                    
                    val continueWatching = mediaList.filter { media ->
                        val p = progressMap[media.id]
                        p != null &&
                            p.duration > 0 &&
                            p.position >= p.duration * RESUME_THRESHOLD &&
                            p.position < p.duration * COMPLETION_THRESHOLD
                    }.sortedByDescending { progressMap[it.id]?.lastUpdated ?: 0L }

                    val exclusionCutoff = System.currentTimeMillis() -
                        exclusionDays.coerceIn(1, 365).toLong() * MILLIS_PER_DAY
                    val spotlightCandidates = if (hideFinished) {
                        mediaList.filterNot { media ->
                            progressMap[media.id]?.let { progress ->
                                progress.isCompleted &&
                                    (progress.completedAt ?: progress.lastUpdated) >= exclusionCutoff
                            } == true
                        }
                    } else {
                        mediaList
                    }
                    val spotlightPool = spotlightCandidates.ifEmpty { mediaList }
                    val lastWatched = continueWatching.firstOrNull()
                    val remainingSpotlight = spotlightPool
                        .filterNot { it.id == lastWatched?.id }
                        .shuffled()
                    val featured = buildList {
                        if (lastWatched != null) add(lastWatched)
                        addAll(remainingSpotlight.take((5 - size).coerceAtLeast(0)))
                    }

                    HomeState.Success(
                        featured = featured,
                        rows = buildList {
                            if (continueWatching.isNotEmpty()) {
                                add(HomeRow("Continue Watching", continueWatching))
                            }
                            add(HomeRow("Recently Added", mediaList))
                            add(HomeRow("Movies", mediaList.filter { it.type == "MOVIE" }.shuffled()))
                            add(HomeRow("TV Shows", mediaList.filter { it.type == "EPISODE" }.shuffled()))
                        },
                        progressMap = progressList.associate { 
                            it.mediaId to (if (it.duration > 0) it.position.toFloat() / it.duration else 0f)
                        },
                        completedMediaIds = progressList
                            .filter { it.isCompleted }
                            .mapTo(mutableSetOf()) { it.mediaId },
                        suggestionGeneration = generation
                    )
                }
            }.collectLatest {
                _homeState.value = it
            }
        }
    }

    fun regenerateSuggestions() {
        suggestionGeneration.value += 1
    }

    fun softRefresh() = regenerateSuggestions()
}

private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1_000L
private const val RESUME_THRESHOLD = 0.05
private const val COMPLETION_THRESHOLD = 0.95

sealed interface HomeState {
    data object Loading : HomeState
    data object Empty : HomeState
    data class Success(
        val featured: List<MediaEntity>,
        val rows: List<HomeRow>,
        val progressMap: Map<Long, Float> = emptyMap(),
        val completedMediaIds: Set<Long> = emptySet(),
        val suggestionGeneration: Int = 0
    ) : HomeState
}

data class HomeRow(
    val title: String,
    val items: List<MediaEntity>
)
