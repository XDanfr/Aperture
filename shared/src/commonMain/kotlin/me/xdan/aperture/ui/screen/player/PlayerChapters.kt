package me.xdan.aperture.ui.screen.player

import androidx.compose.runtime.compositionLocalOf
import me.xdan.aperture.data.jellyfin.JellyfinPlayback

/** One piece of the split progress bar: a Jellyfin segment, or the episode between them. */
data class PlayerChapter(val name: String, val startMs: Long, val endMs: Long)

/** The chapters of what's playing, for the controls; empty for one unbroken bar. */
internal val LocalPlayerChapters = compositionLocalOf { emptyList<PlayerChapter>() }

/** Splits [durationMs] at [segments]; the parts between them are "Episode". Empty without segments. */
fun buildChapters(segments: List<JellyfinPlayback.Segment>, durationMs: Long): List<PlayerChapter> {
    if (segments.isEmpty() || durationMs <= 0) return emptyList()
    val chapters = mutableListOf<PlayerChapter>()
    var cursor = 0L
    segments.sortedBy { it.startMs }.forEach { segment ->
        val start = segment.startMs.coerceIn(cursor, durationMs)
        val end = segment.endMs.coerceIn(start, durationMs)
        if (end - start < 1_000) return@forEach
        if (start - cursor >= 1_000) chapters += PlayerChapter("Episode", cursor, start)
        chapters += PlayerChapter(segment.kind.chapterName, start, end)
        cursor = end
    }
    if (durationMs - cursor >= 1_000) chapters += PlayerChapter("Episode", cursor, durationMs)
    else if (chapters.isNotEmpty()) chapters[chapters.lastIndex] = chapters.last().copy(endMs = durationMs)
    return if (chapters.size > 1) chapters else emptyList()
}

private val JellyfinPlayback.SegmentKind.chapterName: String
    get() = when (this) {
        JellyfinPlayback.SegmentKind.INTRO -> "Intro"
        JellyfinPlayback.SegmentKind.RECAP -> "Recap"
        JellyfinPlayback.SegmentKind.PREVIEW -> "Preview"
        JellyfinPlayback.SegmentKind.COMMERCIAL -> "Ad break"
        JellyfinPlayback.SegmentKind.OUTRO -> "Credits"
    }

/** The chapter [positionMs] falls in. */
fun List<PlayerChapter>.at(positionMs: Long): PlayerChapter? =
    lastOrNull { positionMs >= it.startMs } ?: firstOrNull()

/**
 * Where a chapter jump from [positionMs] lands: the next chapter's start, or going back, this
 * chapter's start (or the previous one's when already near the start), as YouTube does.
 */
fun List<PlayerChapter>.jump(positionMs: Long, direction: Int): Long {
    if (isEmpty()) return positionMs
    val index = indexOf(at(positionMs)).coerceAtLeast(0)
    return if (direction > 0) getOrNull(index + 1)?.startMs ?: positionMs
    else if (positionMs - this[index].startMs > 3_000) this[index].startMs
    else getOrNull(index - 1)?.startMs ?: this[index].startMs
}

/** The interior chapter boundaries as fractions of [durationMs], where the bar splits. */
fun List<PlayerChapter>.splitFractions(durationMs: Long): List<Float> =
    if (durationMs <= 0) emptyList() else drop(1).map { it.startMs.toFloat() / durationMs }
