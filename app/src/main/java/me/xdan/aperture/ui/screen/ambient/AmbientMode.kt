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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import me.xdan.aperture.data.remote.api.TmdbApi
import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.domain.model.AmbientBrandPlacement
import me.xdan.aperture.domain.model.AmbientSettings
import me.xdan.aperture.domain.model.AmbientModeType
import me.xdan.aperture.ui.component.ApertureBrandMark
import me.xdan.aperture.ui.component.ApertureBrandPalette
import me.xdan.aperture.ui.component.artworkApertureBrandPalette
import me.xdan.aperture.ui.component.currentApertureBrandPalette
import me.xdan.aperture.ui.theme.ApertureBrandFontFamily
import me.xdan.aperture.util.BackdropImageUsage
import me.xdan.aperture.util.backdropImageSpec
import me.xdan.aperture.util.backdropImageUrl
import java.util.Date
import kotlin.random.Random

private const val CinematicHoldMillis = 13_000L
private const val CinematicTransitionMillis = 1_800
private const val CinematicFadeOutMillis = 1_200
private const val WallSpinIntervalMillis = 30_000L
private const val PosterColumns = 8
private const val PosterRows = 3

@Composable
fun AmbientMode(
    viewModel: AmbientViewModel = viewModel()
) {
    val hostView = LocalView.current
    val allMedia by viewModel.media.collectAsState()
    val settings by viewModel.settings.collectAsState()

    AmbientScene(allMedia, settings, artworkAccent = viewModel::artworkAccent)
}

@Composable
internal fun AmbientScene(
    allMedia: List<MediaEntity>,
    settings: AmbientSettings,
    animate: Boolean = true,
    artworkAccent: suspend (String) -> Int? = { null }
) {
    val glassBackdrop = rememberAmbientGlassBackdrop()
    CompositionLocalProvider(LocalAmbientGlassBackdrop provides glassBackdrop) {
        when (settings.mode) {
            AmbientModeType.CINEMATIC -> CinematicAmbientMode(
                media = remember(allMedia) { eligibleCinematicMedia(allMedia) },
                showClock = settings.showClock,
                glassBackdrop = glassBackdrop,
                artworkAccent = artworkAccent,
                animate = animate
            )
            AmbientModeType.POSTER_WALL -> PosterWallAmbientMode(
                media = remember(allMedia) { eligiblePosterMedia(allMedia) },
                brandPlacement = settings.wallBrandPlacement,
                showClock = settings.showClock,
                glassBackdrop = glassBackdrop,
                animate = animate
            )
        }
    }
}

@Composable
private fun CinematicAmbientMode(
    media: List<MediaEntity>,
    showClock: Boolean,
    glassBackdrop: AmbientGlassBackdropState,
    artworkAccent: suspend (String) -> Int?,
    animate: Boolean
) {
    val themePalette = currentApertureBrandPalette()
    val mediaKey = remember(media) { media.joinToString("|") { "${it.id}:${it.backdropPath}" } }
    var current by remember(mediaKey) { mutableStateOf(media.randomOrNull()) }
    var logoAccent by remember(mediaKey) { mutableStateOf<Color?>(null) }
    var transitionKey by remember(mediaKey) { mutableIntStateOf(0) }
    val artworkAlpha = remember(mediaKey) { Animatable(0f) }
    val targetPalette = remember(logoAccent, themePalette) {
        logoAccent?.let(::artworkApertureBrandPalette) ?: themePalette
    }
    val palette = animatedAmbientPalette(targetPalette)

    LaunchedEffect(mediaKey, animate) {
        if (!animate) {
            artworkAlpha.snapTo(1f)
            current?.backdropPath?.let { logoAccent = artworkAccent(it)?.let(::Color) }
            return@LaunchedEffect
        }
        artworkAlpha.snapTo(0f)
        current?.backdropPath?.let { path ->
            logoAccent = artworkAccent(path)?.let { Color(it) }
        }
        if (current != null) {
            artworkAlpha.animateTo(
                1f,
                tween(CinematicTransitionMillis, easing = FastOutSlowInEasing)
            )
        }
        while (media.isNotEmpty()) {
            val alternatives = media.filterNot { it.id == current?.id }
            val next = (alternatives.ifEmpty { media }).random()
            val nextAccent = next.backdropPath
                ?.let { artworkAccent(it) }
                ?.let { Color(it) }
            delay(CinematicHoldMillis)
            artworkAlpha.animateTo(
                0f,
                tween(CinematicFadeOutMillis, easing = FastOutSlowInEasing)
            )
            logoAccent = nextAccent
            transitionKey += 1
            current = next
            artworkAlpha.animateTo(
                1f,
                tween(CinematicTransitionMillis, easing = FastOutSlowInEasing)
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AmbientGlassBackdrop(glassBackdrop, Modifier.fillMaxSize()) {
            current?.let { item ->
                CinematicArtwork(
                    media = item,
                    animate = animate,
                    modifier = Modifier.graphicsLayer { alpha = artworkAlpha.value }
                )
            }

            if (current == null) AmbientArtworkEmptyState()
        }

        AmbientBrandCluster(
            palette = palette,
            spinKey = transitionKey,
            showClock = showClock,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 38.dp, top = 34.dp)
        )
    }
}

@Composable
private fun CinematicArtwork(
    media: MediaEntity,
    animate: Boolean,
    modifier: Modifier = Modifier
) {
    val travel = remember(media.id) { Animatable(0f) }
    val travelDistance = with(LocalDensity.current) { 30.dp.toPx() }
    val direction = if (media.id % 2L == 0L) 1f else -1f
    val context = LocalContext.current
    val backdropSpec = remember(context) {
        backdropImageSpec(context, BackdropImageUsage.AMBIENT)
    }

    LaunchedEffect(media.id, animate) {
        if (!animate) return@LaunchedEffect
        travel.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = (CinematicHoldMillis +
                    CinematicTransitionMillis +
                    CinematicFadeOutMillis).toInt(),
                easing = LinearEasing
            )
        )
    }

    Box(modifier.fillMaxSize()) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(backdropImageUrl(media.backdropPath!!, backdropSpec))
                .size(backdropSpec.widthPx, backdropSpec.heightPx)
                .crossfade(false)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.12f
                    scaleY = 1.12f
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
    showClock: Boolean,
    glassBackdrop: AmbientGlassBackdropState,
    animate: Boolean
) {
    val background = MaterialTheme.colorScheme.background
    val palette = currentApertureBrandPalette()
    val accent = palette.play
    val mediaKey = remember(media) { media.joinToString("|") { "${it.id}:${it.posterPath}" } }
    var spinKey by remember(mediaKey, brandPlacement) { mutableIntStateOf(0) }

    LaunchedEffect(mediaKey, brandPlacement, animate) {
        if (!animate) return@LaunchedEffect
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
        AmbientGlassBackdrop(glassBackdrop, Modifier.fillMaxSize().background(background)) {
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
                                        animate = animate,
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
        }

        when (brandPlacement) {
            AmbientBrandPlacement.TOP_LEFT -> AmbientBrandCluster(
                palette = palette,
                spinKey = spinKey,
                showClock = showClock,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 38.dp, top = 34.dp)
            )
            AmbientBrandPlacement.CENTRE -> AmbientGlassSurface(
                accent = accent,
                shape = RoundedCornerShape(72.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(248.dp)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ApertureBrandMark(
                        modifier = Modifier.size(176.dp),
                        spinBlades = spinKey > 0,
                        spinKey = spinKey,
                        palette = palette
                    )
                }
            }
        }

        if (showClock && brandPlacement == AmbientBrandPlacement.CENTRE) {
            AmbientClock(
                accent = accent,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = 164.dp)
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
    animate: Boolean,
    modifier: Modifier = Modifier
) {
    val random = remember(mediaKey, index) { Random(System.nanoTime() + index * 7_919L) }
    var frame by remember(mediaKey, index) {
        mutableStateOf(PosterFrame(media[random.nextInt(media.size)], 0))
    }
    var ready by remember(mediaKey, index) { mutableStateOf(false) }
    val entrance = remember(mediaKey, index) { Animatable(0f) }
    val restingAlpha = remember(mediaKey, index) { 0.17f + random.nextFloat() * 0.20f }

    LaunchedEffect(mediaKey, index, animate) {
        if (!animate) {
            ready = true
            entrance.snapTo(1f)
            return@LaunchedEffect
        }
        delay(index * 85L)
        ready = true
        entrance.animateTo(1f, tween(1_350, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(mediaKey, index, animate) {
        if (!animate) return@LaunchedEffect
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
private fun AmbientBrandCluster(
    palette: ApertureBrandPalette,
    spinKey: Int,
    showClock: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BrandChip(palette = palette, spinKey = spinKey)
        if (showClock) AmbientClock(accent = palette.play)
    }
}

@Composable
private fun BrandChip(
    palette: ApertureBrandPalette,
    spinKey: Int,
    modifier: Modifier = Modifier
) {
    AmbientBrandChip(
        accent = palette.play,
        shape = RoundedCornerShape(28.dp),
        contentPadding = PaddingValues(start = 14.dp, top = 10.dp, end = 24.dp, bottom = 10.dp),
        modifier = modifier
    ) {
        ApertureBrandMark(
            modifier = Modifier.size(46.dp),
            spinBlades = spinKey > 0,
            spinKey = spinKey,
            palette = palette
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = "Aperture",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontFamily = ApertureBrandFontFamily
            ),
            color = palette.play
        )
    }
}

@Composable
private fun AmbientClock(
    accent: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var time by remember { mutableStateOf(DateFormat.getTimeFormat(context).format(Date())) }
    LaunchedEffect(context) {
        while (true) {
            time = DateFormat.getTimeFormat(context).format(Date())
            delay(30_000L)
        }
    }
    AmbientBrandChip(
        accent = accent,
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(horizontal = 21.dp, vertical = 10.dp),
        modifier = modifier
    ) {
        Text(
            text = time,
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White.copy(alpha = 0.90f)
        )
    }
}

@Composable
private fun AmbientBrandChip(
    accent: Color,
    shape: Shape,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    AmbientGlassSurface(accent = accent, shape = shape, modifier = modifier) {
        Row(
            modifier = Modifier.padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

/** Build tonal roles once per title, then animate each role without recalculating HCT per frame. */
@Composable
private fun animatedAmbientPalette(target: ApertureBrandPalette): ApertureBrandPalette {
    val spec = tween<Color>(CinematicTransitionMillis, easing = FastOutSlowInEasing)
    val blade by animateColorAsState(target.blade, spec, label = "ambientBlade")
    val overlap by animateColorAsState(target.overlap, spec, label = "ambientOverlap")
    val play by animateColorAsState(target.play, spec, label = "ambientPlay")
    return target.copy(blade = blade, overlap = overlap, play = play)
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
