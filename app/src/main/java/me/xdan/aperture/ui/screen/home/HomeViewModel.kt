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
import me.xdan.aperture.data.local.entity.PlaybackProgressEntity
import me.xdan.aperture.domain.repository.MediaRepository
import me.xdan.aperture.domain.repository.UserPreferencesRepository
import kotlin.random.Random
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
                combine(repository.getAllMedia(), repository.getAllProgress()) { media, progress ->
                    media to progress
                },
                combine(
                    userPreferencesRepository.hideFinishedFromSpotlight,
                    userPreferencesRepository.finishedSpotlightExclusionDays
                ) { hideFinished, exclusionDays -> hideFinished to exclusionDays },
                suggestionGeneration
            ) { (mediaList, progressList), (hideFinished, exclusionDays), generation ->
                buildHomeState(mediaList, progressList, hideFinished, exclusionDays, generation)
            }.collectLatest { _homeState.value = it }
        }
    }

    fun regenerateSuggestions() {
        suggestionGeneration.value += 1
    }

    fun softRefresh() = regenerateSuggestions()
}

private fun buildHomeState(
    mediaList: List<MediaEntity>,
    progressList: List<PlaybackProgressEntity>,
    hideFinished: Boolean,
    exclusionDays: Int,
    generation: Int
): HomeState {
    if (mediaList.isEmpty()) return HomeState.Empty

    val progressMap = progressList.associateBy { it.mediaId }
    val movies = mediaList.filter { it.type == "MOVIE" }
    val episodeGroups = mediaList.filter { it.type == "EPISODE" }
        .groupBy { it.title }
    val showCards = episodeGroups.values.mapNotNull { episodes ->
        episodes.firstOrNull { !it.posterPath.isNullOrBlank() } ?: episodes.firstOrNull()
    }.sortedBy { it.title.lowercase() }
    val libraryCards = (movies + showCards).distinctBy { it.id }

    val movieContinue = movies.mapNotNull { media ->
        val progress = progressMap[media.id] ?: return@mapNotNull null
        if (
            progress.duration > 0 &&
            progress.position >= progress.duration * RESUME_THRESHOLD &&
            progress.position < progress.duration * COMPLETION_THRESHOLD
        ) media to progress.lastUpdated else null
    }
    val episodeContinue = episodeGroups.values.mapNotNull { episodes ->
        nextEpisodeForContinueWatching(episodes, progressMap)
    }
    val continueWatchingWithTime = (movieContinue + episodeContinue)
        .sortedByDescending { it.second }
    val continueWatching = continueWatchingWithTime.map { it.first }

    val exclusionCutoff = System.currentTimeMillis() -
        exclusionDays.coerceIn(1, 365).toLong() * MILLIS_PER_DAY
    val spotlightCandidates = if (hideFinished) {
        libraryCards.filterNot { media ->
            if (media.type == "EPISODE") {
                val showProgress = episodeGroups[media.title].orEmpty().mapNotNull { progressMap[it.id] }
                showProgress.isNotEmpty() && showProgress.all { progress ->
                    progress.isCompleted && (progress.completedAt ?: progress.lastUpdated) >= exclusionCutoff
                }
            } else {
                progressMap[media.id]?.let { progress ->
                    progress.isCompleted && (progress.completedAt ?: progress.lastUpdated) >= exclusionCutoff
                } == true
            }
        }
    } else libraryCards
    val spotlightPool = spotlightCandidates.ifEmpty { libraryCards }
    val lastWatched = continueWatching.firstOrNull()
    val suggestionSeed = mediaList.fold(generation * 31 + 17) { seed, media ->
        seed * 31 + media.id.hashCode()
    }
    val remainingSpotlight = spotlightPool
        .filterNot { candidate ->
            candidate.id == lastWatched?.id ||
                (candidate.type == "EPISODE" && lastWatched?.type == "EPISODE" &&
                    candidate.title == lastWatched.title)
        }
        .shuffled(Random(suggestionSeed xor SPOTLIGHT_SEED_SALT))
    val featured = buildList {
        if (lastWatched != null) add(lastWatched)
        addAll(remainingSpotlight.take((5 - size).coerceAtLeast(0)))
    }

    return HomeState.Success(
        featured = featured,
        rows = buildList {
            if (continueWatching.isNotEmpty()) add(HomeRow("Continue Watching", continueWatching))
            add(HomeRow("Recently Added", libraryCards.sortedByDescending { it.dateAdded }))
            add(HomeRow("Movies", movies.shuffled(Random(suggestionSeed xor MOVIES_SEED_SALT))))
            add(HomeRow("TV Shows", showCards.shuffled(Random(suggestionSeed xor SHOWS_SEED_SALT))))
        },
        progressMap = progressList.associate {
            it.mediaId to (if (it.duration > 0) it.position.toFloat() / it.duration else 0f)
        },
        completedMediaIds = progressList.filter { it.isCompleted }.mapTo(mutableSetOf()) { it.mediaId },
        continueMediaIds = continueWatching.mapTo(mutableSetOf()) { it.id },
        suggestionGeneration = generation
    )
}

/** Returns the one episode a show contributes to Continue Watching and its ordering timestamp. */
internal fun nextEpisodeForContinueWatching(
    episodes: List<MediaEntity>,
    progressMap: Map<Long, PlaybackProgressEntity>
): Pair<MediaEntity, Long>? {
    val ordered = episodes.sortedWith(
        compareBy<MediaEntity>({ it.seasonNumber ?: Int.MAX_VALUE }, { it.episodeNumber ?: Int.MAX_VALUE }, { it.filePath })
    )
    val latestRelevant = ordered.mapNotNull { episode ->
        progressMap[episode.id]?.takeIf { it.isCompleted || it.keepInContinueWatching }
            ?.let { episode to it }
    }.maxByOrNull { it.second.lastUpdated } ?: return null

    val (latestEpisode, progress) = latestRelevant
    if (!progress.isCompleted && progress.keepInContinueWatching) {
        return latestEpisode to progress.lastUpdated
    }
    val nextIndex = ordered.indexOfFirst { it.id == latestEpisode.id } + 1
    val nextEpisode = ordered.getOrNull(nextIndex) ?: return null
    return nextEpisode to progress.lastUpdated
}

private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1_000L
private const val RESUME_THRESHOLD = 0.05
private const val COMPLETION_THRESHOLD = 0.95
private const val SPOTLIGHT_SEED_SALT = 0x5F3759DF
private const val MOVIES_SEED_SALT = 0x13579BDF
private const val SHOWS_SEED_SALT = 0x02468ACE

sealed interface HomeState {
    data object Loading : HomeState
    data object Empty : HomeState
    data class Success(
        val featured: List<MediaEntity>,
        val rows: List<HomeRow>,
        val progressMap: Map<Long, Float> = emptyMap(),
        val completedMediaIds: Set<Long> = emptySet(),
        val continueMediaIds: Set<Long> = emptySet(),
        val suggestionGeneration: Int = 0
    ) : HomeState
}

data class HomeRow(val title: String, val items: List<MediaEntity>)
