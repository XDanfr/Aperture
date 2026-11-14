package me.xdan.aperture.mobile.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.CarouselState
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.xdan.aperture.companion.protocol.CompanionMedia
import me.xdan.aperture.mobile.data.Library
import me.xdan.aperture.mobile.data.LibraryEntry
import me.xdan.aperture.mobile.data.episodeLabel
import me.xdan.aperture.mobile.data.isInProgress
import me.xdan.aperture.mobile.data.progressFraction
import me.xdan.aperture.mobile.ui.components.Artwork
import me.xdan.aperture.mobile.ui.components.DotSeparatedRow
import me.xdan.aperture.mobile.ui.components.EmptyState
import me.xdan.aperture.mobile.ui.components.PosterRow
import me.xdan.aperture.mobile.ui.components.SectionTitle
import me.xdan.aperture.mobile.ui.theme.CompanionMotion
import me.xdan.aperture.mobile.ui.theme.brandPalette
import me.xdan.aperture.ui.component.ApertureBrandArtwork
import me.xdan.aperture.ui.component.ApertureBrandPose
import me.xdan.aperture.ui.component.animateBrandTurn
import me.xdan.aperture.ui.theme.ApertureBrandFontFamily

/** one spotlight page: a title with its artwork and what Play should start. */
private data class SpotlightPick(
    val entry: LibraryEntry,
    val play: CompanionMedia,
    val posterPath: String?,
    val backdropPath: String?,
    val meta: List<String>,
    val overview: String?
)

private const val SPOTLIGHT_COUNT = 6

private fun spotlightPicks(library: Library): List<SpotlightPick> =
    library.recentlyAdded.mapNotNull { entry ->
        when (entry) {
            is LibraryEntry.Movie -> {
                val media = entry.media
                SpotlightPick(
                    entry = entry,
                    play = media,
                    posterPath = media.posterPath,
                    backdropPath = media.backdropPath,
                    meta = listOfNotNull(media.year?.toString(), media.durationMs?.let(::formatRuntime), "Movie"),
                    overview = media.overview
                )
            }
            is LibraryEntry.Show -> {
                val group = entry.group
                val seasons = group.seasons.size
                SpotlightPick(
                    entry = entry,
                    play = group.nextUp,
                    posterPath = group.posterPath,
                    backdropPath = group.backdropPath,
                    meta = listOfNotNull(
                        group.year?.toString(),
                        if (seasons > 1) "$seasons seasons" else null,
                        "TV show"
                    ),
                    overview = group.overview
                )
            }
        }.takeIf { it.posterPath != null || it.backdropPath != null }
    }.take(SPOTLIGHT_COUNT)

/** height of the branded bar pinned over the top of Home, below the status bar. */
private val HomeBarHeight = 64.dp

/** how much of the first row peeks above the bottom bars beneath the spotlight. */
private val FirstRowPeek = 112.dp

/**
 * home: a full-screen spotlight with the first row peeking up underneath.
 * scrolling down slides it away, and a half scroll snaps to the nearest end.
 * [topInset] is the status bar height (zero while the connection banner covers it).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    library: Library,
    topInset: Dp,
    bottomPadding: Dp,
    canPlay: Boolean,
    onOpen: (LibraryEntry) -> Unit,
    onPlay: (CompanionMedia) -> Unit,
    onResume: (CompanionMedia) -> Unit
) {
    val picks = remember(library) { spotlightPicks(library) }
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val headerHeight = topInset + HomeBarHeight
        val spotlightHeight = (maxHeight - bottomPadding - FirstRowPeek).coerceIn(360.dp, 760.dp)
        // scroll distance at which the spotlight's bottom edge meets the pinned bar.
        val dismissPx = with(density) { (spotlightHeight - headerHeight).toPx() }
        val hasSpotlight = picks.isNotEmpty()

        // 0 = spotlight fully shown, 1 = scrolled away.
        val dismissed by remember(hasSpotlight, dismissPx) {
            derivedStateOf {
                when {
                    !hasSpotlight || listState.firstVisibleItemIndex > 0 -> 1f
                    else -> (listState.firstVisibleItemScrollOffset / dismissPx).coerceIn(0f, 1f)
                }
            }
        }

        // a half-finished scroll settles on either the spotlight or the rows below it.
        if (hasSpotlight) {
            LaunchedEffect(listState, dismissPx) {
                fun offset() = if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else Float.MAX_VALUE
                var startOffset = 0f
                snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
                    if (scrolling) {
                        startOffset = offset()
                        return@collect
                    }
                    val offset = offset()
                    if (offset <= 1f || offset >= dismissPx - 1f) return@collect
                    // follow the gesture's direction: a short pull either way is enough.
                    val towardsRows = offset > startOffset
                    val threshold = if (towardsRows) 0.2f else 0.8f
                    val target = if (offset > dismissPx * threshold) dismissPx else 0f
                    listState.animateScrollBy(target - offset, spring(stiffness = Spring.StiffnessMediumLow))
                }
            }
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                top = if (hasSpotlight) 0.dp else headerHeight,
                bottom = bottomPadding + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (hasSpotlight) {
                item(key = "spotlight") {
                    Spotlight(
                        picks = picks,
                        topInset = topInset,
                        canPlay = canPlay,
                        onOpen = onOpen,
                        onPlay = onPlay,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(spotlightHeight)
                            .graphicsLayer {
                                // parallax: the rows slide up over the spotlight as it recedes.
                                val offset = if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset else 0
                                translationY = offset * 0.45f
                                alpha = 1f - dismissed
                            }
                    )
                }
            }
            if (library.items.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        "Your library is empty",
                        "Add folders on the TV in Settings › Media folders, and they'll appear here."
                    )
                }
            }
            if (library.continueWatching.isNotEmpty()) {
                item(key = "continue") { ContinueWatching(library.continueWatching, onResume) }
            }
            item(key = "recent") { PosterRow("Recently added", library.recentlyAdded, onOpen) }
            item(key = "mylist") { PosterRow("My List", library.favourites, onOpen) }
            item(key = "movies") { PosterRow("Movies", library.movies.take(20).map(LibraryEntry::Movie), onOpen) }
            item(key = "shows") { PosterRow("TV shows", library.shows.take(20).map(LibraryEntry::Show), onOpen) }
        }

        HomeBar(topInset = topInset, solid = { dismissed })
    }
}

/** the pinned Aperture bar: transparent over the spotlight, solid once it has scrolled away. */
@Composable
private fun HomeBar(topInset: Dp, solid: () -> Float) {
    val surface = MaterialTheme.colorScheme.surface
    val collapsed by remember { derivedStateOf { solid() >= 1f } }
    val rotation = remember { Animatable(0f) }
    val first = remember { booleanArrayOf(true) }
    // the shutter turns once each time the spotlight is dismissed or brought back.
    LaunchedEffect(collapsed) {
        if (first[0]) first[0] = false else rotation.animateBrandTurn(CompanionMotion)
    }
    val palette = MaterialTheme.colorScheme.brandPalette()
    // same glass chip as the TV's Ambient Mode, so the brand stands apart from artwork.
    val chip = lerp(MaterialTheme.colorScheme.background, palette.play, 0.06f).copy(alpha = 0.82f)
    Box(
        Modifier
            .fillMaxWidth()
            .drawBehind { drawRect(surface.copy(alpha = solid())) }
            .padding(top = topInset)
            .height(HomeBarHeight)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(chip)
                .padding(start = 10.dp, top = 6.dp, end = 18.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ApertureBrandArtwork(
                modifier = Modifier.size(32.dp),
                palette = palette,
                pose = { ApertureBrandPose(rotation = rotation.value) }
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Aperture",
                style = MaterialTheme.typography.titleLarge,
                fontFamily = ApertureBrandFontFamily,
                color = palette.play,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Spotlight(
    picks: List<SpotlightPick>,
    topInset: Dp,
    canPlay: Boolean,
    onOpen: (LibraryEntry) -> Unit,
    onPlay: (CompanionMedia) -> Unit,
    modifier: Modifier = Modifier
) {
    val state = rememberCarouselState { picks.size }
    BoxWithConstraints(modifier) {
        val landscape = maxWidth > maxHeight
        val pageWidth = maxWidth
        // full-screen carousel: one item fills the width, advancing a page per swipe.
        HorizontalUncontainedCarousel(
            state = state,
            itemWidth = pageWidth,
            flingBehavior = CarouselDefaults.singleAdvanceFlingBehavior(state),
            modifier = Modifier.fillMaxSize()
        ) { index ->
            SpotlightPage(
                pick = picks[index],
                landscape = landscape,
                topInset = topInset,
                canPlay = canPlay,
                onOpen = onOpen,
                onPlay = onPlay,
                modifier = Modifier.fillMaxSize().maskClip(androidx.compose.ui.graphics.RectangleShape)
            )
        }
        if (picks.size > 1) {
            PageDots(
                state = state,
                count = picks.size,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SpotlightPage(
    pick: SpotlightPick,
    landscape: Boolean,
    topInset: Dp,
    canPlay: Boolean,
    onOpen: (LibraryEntry) -> Unit,
    onPlay: (CompanionMedia) -> Unit,
    modifier: Modifier = Modifier
) {
    val background = MaterialTheme.colorScheme.background
    Box(modifier.clickable { onOpen(pick.entry) }) {
        // posters suit the tall phone spotlight; backdrops suit an unfolded, wide one.
        Artwork(
            path = if (landscape) pick.backdropPath ?: pick.posterPath else pick.posterPath ?: pick.backdropPath,
            size = if (landscape && pick.backdropPath != null) "w1280" else "w780",
            contentDescription = pick.entry.title,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier.fillMaxWidth().height(topInset + HomeBarHeight + 56.dp).background(
                Brush.verticalGradient(listOf(background.copy(alpha = 0.6f), Color.Transparent))
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.35f to Color.Transparent,
                    0.72f to background.copy(alpha = 0.82f),
                    1f to background
                )
            )
        )
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("New on your TV", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                pick.entry.title,
                style = MaterialTheme.typography.displaySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (pick.meta.isNotEmpty()) DotSeparatedRow(pick.meta)
            pick.overview?.takeIf(String::isNotBlank)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                val resume = pick.play.isInProgress
                Button(
                    onClick = { onPlay(pick.play) },
                    enabled = canPlay,
                    shapes = ButtonDefaults.shapes()
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(
                        when {
                            pick.entry is LibraryEntry.Show -> pick.play.episodeLabel?.let { "${if (resume) "Resume" else "Play"} $it" } ?: "Play on TV"
                            resume -> "Resume on TV"
                            else -> "Play on TV"
                        }
                    )
                }
                FilledTonalButton(onClick = { onOpen(pick.entry) }, shapes = ButtonDefaults.shapes()) {
                    Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Details")
                }
            }
        }
    }
}

@Composable
private fun PageDots(state: CarouselState, count: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { index ->
            val selected = state.currentItem == index
            val width by animateDpAsState(if (selected) 20.dp else 6.dp, CompanionMotion.brandFastSpatial(), label = "dot")
            Box(
                Modifier.size(width = width, height = 6.dp).clip(CircleShape).background(
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContinueWatching(items: List<CompanionMedia>, onResume: (CompanionMedia) -> Unit) {
    Column {
        SectionTitle("Continue watching")
        HorizontalMultiBrowseCarousel(
            state = rememberCarouselState { items.size },
            preferredItemWidth = 300.dp,
            itemSpacing = 8.dp,
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.fillMaxWidth().height(200.dp)
        ) { index ->
            val media = items[index]
            Box(
                Modifier.fillMaxSize()
                    .maskClip(MaterialTheme.shapes.extraLarge)
                    .clickable { onResume(media) }
            ) {
                Artwork(
                    path = media.stillPath ?: media.backdropPath ?: media.posterPath,
                    size = "w780",
                    contentDescription = media.title,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.82f))
                    )
                )
                Column(
                    Modifier.align(Alignment.BottomStart).padding(16.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        media.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    media.episodeLabel?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
                    }
                    // a still line: Home is a resting view, so progress stays calm here.
                    LinearProgressIndicator(
                        progress = { media.progressFraction },
                        strokeCap = StrokeCap.Round,
                        gapSize = 4.dp,
                        drawStopIndicator = {},
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
