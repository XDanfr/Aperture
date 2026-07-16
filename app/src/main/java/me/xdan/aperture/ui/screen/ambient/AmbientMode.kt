package me.xdan.aperture.ui.screen.ambient

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.data.remote.api.TmdbApi
import me.xdan.aperture.domain.model.AmbientBrandPlacement
import me.xdan.aperture.domain.model.AmbientModeType
import me.xdan.aperture.ui.component.ApertureBrandMark
import me.xdan.aperture.ui.theme.ApertureBrandFontFamily
import java.util.Date
import kotlin.random.Random

private const val CinematicHoldMillis = 13_000L
private const val CinematicTransitionMillis = 1_800
private const val WallSpinIntervalMillis = 30_000L
private const val PosterColumns = 8
private const val PosterRows = 3

@Composable
fun AmbientMode(
    viewModel: AmbientViewModel = viewModel()
) {
    val allMedia by viewModel.media.collectAsState()
    val settings by viewModel.settings.collectAsState()

    when (settings.mode) {
        AmbientModeType.CINEMATIC -> CinematicAmbientMode(
            media = remember(allMedia) { eligibleCinematicMedia(allMedia) },
            showClock = settings.showClock,
            artworkAccent = viewModel::artworkAccent
        )
        AmbientModeType.POSTER_WALL -> PosterWallAmbientMode(
            media = remember(allMedia) { eligiblePosterMedia(allMedia) },
            brandPlacement = settings.wallBrandPlacement,
            showClock = settings.showClock
        )
    }
}

@Composable
private fun CinematicAmbientMode(
    media: List<MediaEntity>,
    showClock: Boolean,
    artworkAccent: suspend (String) -> Int?
) {
    val themeAccent = MaterialTheme.colorScheme.primary
    val currentThemeAccent by rememberUpdatedState(themeAccent)
    val mediaKey = remember(media) { media.joinToString("|") { "${it.id}:${it.backdropPath}" } }
    var current by remember(mediaKey) { mutableStateOf(media.randomOrNull()) }
    var logoAccent by remember(mediaKey) { mutableStateOf(themeAccent) }
    var transitionKey by remember(mediaKey) { mutableIntStateOf(0) }
    val animatedLogoAccent by animateColorAsState(
        targetValue = logoAccent,
        animationSpec = tween(CinematicTransitionMillis, easing = FastOutSlowInEasing),
        label = "ambientArtworkAccent"
    )

    LaunchedEffect(mediaKey) {
        current?.backdropPath?.let { path ->
            logoAccent = artworkAccent(path)?.let { Color(it) } ?: currentThemeAccent
        }
        while (media.isNotEmpty()) {
            delay(CinematicHoldMillis)
            val alternatives = media.filterNot { it.id == current?.id }
            val next = (alternatives.ifEmpty { media }).random()
            val nextAccent = next.backdropPath
                ?.let { artworkAccent(it) }
                ?.let { Color(it) }
                ?: currentThemeAccent
            logoAccent = nextAccent
            transitionKey += 1
            current = next
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AnimatedContent(
            targetState = current,
            transitionSpec = {
                fadeIn(tween(CinematicTransitionMillis, easing = FastOutSlowInEasing)) togetherWith
                    fadeOut(tween(CinematicTransitionMillis, easing = FastOutSlowInEasing))
            },
            contentKey = { it?.id },
            label = "ambientCinematicArtwork"
        ) { item ->
            if (item != null) CinematicArtwork(item)
        }

        if (current == null) AmbientArtworkEmptyState()

        BrandChip(
            accent = animatedLogoAccent,
            spinKey = transitionKey,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 38.dp, top = 34.dp)
        )
        if (showClock) {
            AmbientClock(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 42.dp, top = 38.dp)
            )
        }
    }
}

@Composable
private fun CinematicArtwork(media: MediaEntity) {
    val appearance = remember(media.id) { Animatable(0f) }
    val travel = remember(media.id) { Animatable(0f) }
    val travelDistance = with(LocalDensity.current) { 58.dp.toPx() }
    val direction = if (media.id % 2L == 0L) 1f else -1f

    LaunchedEffect(media.id) {
        appearance.animateTo(1f, tween(CinematicTransitionMillis, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(media.id) {
        travel.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = (CinematicHoldMillis + CinematicTransitionMillis).toInt(),
                easing = LinearEasing
            )
        )
    }

    Box(Modifier.fillMaxSize().graphicsLayer { alpha = appearance.value }) {
        AsyncImage(
            model = TmdbApi.IMAGE_BASE_URL + "w1280" + media.backdropPath,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.08f
                    scaleY = 1.08f
                    translationX = ((travel.value * 2f) - 1f) * travelDistance * direction
                }
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Black.copy(alpha = 0.04f),
                            0.55f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.74f)
                        )
                    )
                )
        )
        Text(
            text = media.title,
            style = MaterialTheme.typography.displayMedium,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.72f)
                .padding(start = 54.dp, end = 32.dp, bottom = 48.dp)
        )
    }
}

@Composable
private fun PosterWallAmbientMode(
    media: List<MediaEntity>,
    brandPlacement: AmbientBrandPlacement,
    showClock: Boolean
) {
    val background = MaterialTheme.colorScheme.background
    val accent = MaterialTheme.colorScheme.primary
    val mediaKey = remember(media) { media.joinToString("|") { "${it.id}:${it.posterPath}" } }
    var spinKey by remember(mediaKey, brandPlacement) { mutableIntStateOf(0) }

    LaunchedEffect(mediaKey, brandPlacement) {
        while (true) {
            delay(WallSpinIntervalMillis)
            spinKey += 1
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
    ) {
        if (media.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 26.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                repeat(PosterRows) { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        repeat(PosterColumns) { column ->
                            val index = row * PosterColumns + column
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                AmbientPosterSlot(
                                    media = media,
                                    mediaKey = mediaKey,
                                    index = index,
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .aspectRatio(2f / 3f)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            AmbientArtworkEmptyState()
        }

        when (brandPlacement) {
            AmbientBrandPlacement.TOP_LEFT -> BrandChip(
                accent = accent,
                spinKey = spinKey,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 38.dp, top = 34.dp)
            )
            AmbientBrandPlacement.CENTRE -> Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(272.dp)
                    .background(background, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(210.dp)
                        .blur(28.dp)
                        .background(accent.copy(alpha = 0.18f), CircleShape)
                )
                ApertureBrandMark(
                    modifier = Modifier.size(176.dp),
                    accent = accent,
                    spinBlades = spinKey > 0,
                    spinKey = spinKey
                )
            }
        }

        if (showClock) {
            AmbientClock(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 42.dp, top = 38.dp)
            )
        }
    }
}

private data class PosterFrame(val media: MediaEntity, val generation: Int)

@Composable
private fun AmbientPosterSlot(
    media: List<MediaEntity>,
    mediaKey: String,
    index: Int,
    modifier: Modifier = Modifier
) {
    val random = remember(mediaKey, index) { Random(System.nanoTime() + index * 7_919L) }
    var frame by remember(mediaKey, index) {
        mutableStateOf(PosterFrame(media[random.nextInt(media.size)], 0))
    }
    var ready by remember(mediaKey, index) { mutableStateOf(false) }
    val entrance = remember(mediaKey, index) { Animatable(0f) }
    val restingAlpha = remember(mediaKey, index) { 0.17f + random.nextFloat() * 0.20f }

    LaunchedEffect(mediaKey, index) {
        delay(index * 85L)
        ready = true
        entrance.animateTo(1f, tween(1_350, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(mediaKey, index) {
        delay(6_000L + random.nextLong(7_000L))
        var generation = 0
        while (true) {
            generation += 1
            frame = PosterFrame(media[random.nextInt(media.size)], generation)
            delay(7_000L + random.nextLong(8_000L))
        }
    }

    Box(modifier = modifier.graphicsLayer { alpha = entrance.value * restingAlpha }) {
        if (ready) {
            AnimatedContent(
                targetState = frame,
                transitionSpec = {
                    fadeIn(tween(1_500, easing = FastOutSlowInEasing)) togetherWith
                        fadeOut(tween(1_500, easing = FastOutSlowInEasing))
                },
                contentKey = { it.generation },
                label = "ambientPoster$index"
            ) { posterFrame ->
                AsyncImage(
                    model = TmdbApi.IMAGE_BASE_URL + "w500" + posterFrame.media.posterPath,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(14.dp))
                )
            }
        }
    }
}

@Composable
private fun BrandChip(
    accent: Color,
    spinKey: Int,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .blur(22.dp)
                .background(accent.copy(alpha = 0.32f), CircleShape)
        )
        Row(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            accent.copy(alpha = 0.28f),
                            Color.Black.copy(alpha = 0.48f)
                        )
                    ),
                    CircleShape
                )
                .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                .padding(start = 12.dp, top = 8.dp, end = 22.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ApertureBrandMark(
                modifier = Modifier.size(46.dp),
                accent = accent,
                spinBlades = spinKey > 0,
                spinKey = spinKey
            )
            Spacer(Modifier.width(9.dp))
            Text(
                text = "Aperture",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = ApertureBrandFontFamily
                ),
                color = accent
            )
        }
    }
}

@Composable
private fun AmbientClock(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var time by remember { mutableStateOf(DateFormat.getTimeFormat(context).format(Date())) }
    LaunchedEffect(context) {
        while (true) {
            time = DateFormat.getTimeFormat(context).format(Date())
            delay(30_000L)
        }
    }
    Text(
        text = time,
        style = MaterialTheme.typography.headlineMedium,
        color = Color.White.copy(alpha = 0.86f),
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.28f), CircleShape)
            .padding(horizontal = 19.dp, vertical = 9.dp)
    )
}

@Composable
private fun AmbientArtworkEmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ApertureBrandMark(
            modifier = Modifier.size(120.dp),
            accent = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = "Add artwork to see Ambient Mode",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.58f)
        )
    }
}

private fun eligibleCinematicMedia(media: List<MediaEntity>): List<MediaEntity> = media
    .asSequence()
    .filterNot { it.isHidden }
    .filter { !it.backdropPath.isNullOrBlank() }
    .distinctBy { it.title.lowercase() to it.backdropPath }
    .toList()

private fun eligiblePosterMedia(media: List<MediaEntity>): List<MediaEntity> = media
    .asSequence()
    .filterNot { it.isHidden }
    .filter { !it.posterPath.isNullOrBlank() }
    .distinctBy { it.title.lowercase() to it.posterPath }
    .toList()

private fun <T> List<T>.randomOrNull(): T? = if (isEmpty()) null else random()
