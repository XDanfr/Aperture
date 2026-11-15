package me.xdan.aperture.domain.playback

import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.data.local.entity.PlaybackProgressEntity

/** Returns the one episode a show contributes to Continue Watching and its ordering timestamp. */
fun nextEpisodeForContinueWatching(
    episodes: List<MediaEntity>,
    progressMap: Map<Long, PlaybackProgressEntity>
): Pair<MediaEntity, Long>? {
    val ordered = episodes.sortedWith(
        compareBy<MediaEntity>({ it.seasonNumber ?: Int.MAX_VALUE }, { it.episodeNumber ?: Int.MAX_VALUE }, { it.filePath })
    )
    val latestRelevant = ordered.mapNotNull { episode ->
        progressMap[episode.id]?.takeIf {
            it.isCompleted || it.keepInContinueWatching ||
                (it.duration > 0 && it.position >= it.duration * 0.05 && it.position < it.duration * 0.95)
        }
            ?.let { episode to it }
    }.maxByOrNull { it.second.lastUpdated } ?: return null

    val (latestEpisode, progress) = latestRelevant
    if (!progress.isCompleted) {
        return latestEpisode to progress.lastUpdated
    }
    val nextIndex = ordered.indexOfFirst { it.id == latestEpisode.id } + 1
    val nextEpisode = ordered.getOrNull(nextIndex) ?: return null
    return nextEpisode to progress.lastUpdated
}

