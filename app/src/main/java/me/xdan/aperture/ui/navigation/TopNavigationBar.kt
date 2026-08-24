package me.xdan.aperture.ui.navigation

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.xdan.aperture.ui.component.ApertureBrandMark
import me.xdan.aperture.ui.theme.ApertureBrandFontFamily
import me.xdan.aperture.ui.theme.ApertureTheme

private val OuterShape = RoundedCornerShape(30.dp)
private val SelectedShape = RoundedCornerShape(28.dp)
private val OuterHeight = 56.dp
private val SlotWidth = 36.dp
private val SlotGap = 4.dp
private val OuterSidePadding = 24.dp
private val SelectedWidths = listOf(
    86.dp,
    94.dp,
    92.dp,
    108.dp,
    94.dp,
    102.dp,
)

private data class NavigationItem(
    val destination: Destination,
    val label: String,
)

@Composable
fun TopNavigationBar(
    currentDestination: Destination,
    onDestinationClick: (Destination) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val items = remember {
        listOf(
            NavigationItem(Destination.Shows, "Shows"),
            NavigationItem(Destination.Movies, "Movies"),
            NavigationItem(Destination.Search, "Search"),
            NavigationItem(Destination.Home, "Aperture"),
            NavigationItem(Destination.MyList, "My List"),
            NavigationItem(Destination.Settings, "Settings"),
        )
    }
    val requesters = remember { List(items.size) { FocusRequester() } }
    var focusedIndex by remember {
        mutableIntStateOf(
            items.indexOfFirst { it.destination.focusKey() == currentDestination.focusKey() }.coerceAtLeast(0)
        )
    }

    LaunchedEffect(currentDestination) {
        val index = items.indexOfFirst { it.destination.focusKey() == currentDestination.focusKey() }
        if (index >= 0) {
            focusedIndex = index
            delay(80)
            runCatching { requesters[index].requestFocus() }
        }
    }

    val selectedWidth by animateDpAsState(
        targetValue = SelectedWidths.getOrElse(focusedIndex) { 94.dp },
        animationSpec = tween(240),
        label = "top-navigation-selected-width",
    )
    val selectedX by animateDpAsState(
        targetValue = OuterSidePadding + (SlotWidth + SlotGap) * focusedIndex - (selectedWidth - SlotWidth) / 2,
        animationSpec = tween(240),
        label = "top-navigation-selected-x",
    )

    Surface(
        shape = OuterShape,
        colors = SurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f),
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Box(
            modifier = Modifier
                .width(274.dp)
                .height(OuterHeight),
        ) {
            Box(
                modifier = Modifier
                    .offset(x = selectedX)
                    .requiredWidth(selectedWidth)
                    .height(OuterHeight)
                    .background(
                        color = ApertureTheme.colorScheme.primaryContainer,
                        shape = SelectedShape,
                    ),
            )

            Row(
                modifier = Modifier
                    .padding(horizontal = OuterSidePadding)
                    .height(OuterHeight),
                horizontalArrangement = Arrangement.spacedBy(SlotGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEachIndexed { index, item ->
                    val requester = requesters[index]
                    val isFocused = focusedIndex == index

                    Box(
                        modifier = Modifier
                            .width(SlotWidth)
                            .height(OuterHeight)
                            .focusRequester(requester)
                            .focusProperties {
                                up = FocusRequester.Cancel
                            }
                            .focusable()
                            .onFocusChanged { state ->
                                if (state.isFocused && focusedIndex != index) {
                                    focusedIndex = index
                                    onDestinationClick(item.destination)
                                }
                            }
                            .clickable {
                                scope.launch {
                                    delay(40)
                                    focusManager.moveFocus(FocusDirection.Down)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            modifier = Modifier.requiredWidth(if (isFocused) selectedWidth else SlotWidth),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            NavigationIcon(
                                destination = item.destination,
                                modifier = Modifier.size(24.dp),
                            )
                            if (isFocused) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = item.label,
                                    maxLines = 1,
                                    softWrap = false,
                                    fontFamily = ApertureBrandFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigationIcon(
    destination: Destination,
    modifier: Modifier,
) {
    when (destination) {
        Destination.Home -> ApertureBrandMark(modifier = modifier)
        Destination.Shows -> SimpleNavigationIcon(modifier, NavigationGlyph.Tv)
        Destination.Movies -> SimpleNavigationIcon(modifier, NavigationGlyph.Movie)
        Destination.Search -> SimpleNavigationIcon(modifier, NavigationGlyph.Search)
        Destination.MyList -> SimpleNavigationIcon(modifier, NavigationGlyph.Favorite)
        Destination.Settings -> SimpleNavigationIcon(modifier, NavigationGlyph.Settings)
        is Destination.Player -> Unit
    }
}

private enum class NavigationGlyph {
    Tv,
    Movie,
    Search,
    Favorite,
    Settings,
}

@Composable
private fun SimpleNavigationIcon(
    modifier: Modifier,
    glyph: NavigationGlyph,
) {
    val contentColor = MaterialTheme.colorScheme.onSurface
    val surfaceColor = ApertureTheme.colorScheme.surface

    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.11f
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
        val w = size.width
        val h = size.height

        when (glyph) {
            NavigationGlyph.Tv -> {
                drawRoundRect(
                    color = contentColor,
                    topLeft = androidx.compose.ui.geometry.Offset(w * 0.12f, h * 0.22f),
                    size = androidx.compose.ui.geometry.Size(w * 0.76f, h * 0.56f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f),
                    style = stroke,
                )
                drawLine(contentColor, androidx.compose.ui.geometry.Offset(w * 0.36f, h * 0.86f), androidx.compose.ui.geometry.Offset(w * 0.64f, h * 0.86f), strokeWidth, StrokeCap.Round)
            }
            NavigationGlyph.Movie -> {
                drawRoundRect(
                    color = contentColor,
                    topLeft = androidx.compose.ui.geometry.Offset(w * 0.12f, h * 0.22f),
                    size = androidx.compose.ui.geometry.Size(w * 0.76f, h * 0.56f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f),
                    style = stroke,
                )
                for (i in 0..2) {
                    val x = w * (0.27f + i * 0.23f)
                    drawLine(contentColor, androidx.compose.ui.geometry.Offset(x, h * 0.23f), androidx.compose.ui.geometry.Offset(x, h * 0.78f), strokeWidth * 0.8f, StrokeCap.Round)
                }
            }
            NavigationGlyph.Search -> {
                drawCircle(contentColor, radius = w * 0.28f, center = androidx.compose.ui.geometry.Offset(w * 0.42f, h * 0.42f), style = stroke)
                drawLine(contentColor, androidx.compose.ui.geometry.Offset(w * 0.61f, h * 0.61f), androidx.compose.ui.geometry.Offset(w * 0.83f, h * 0.83f), strokeWidth, StrokeCap.Round)
            }
            NavigationGlyph.Favorite -> {
                val path = Path().apply {
                    moveTo(w * 0.50f, h * 0.81f)
                    cubicTo(w * 0.42f, h * 0.72f, w * 0.17f, h * 0.55f, w * 0.17f, h * 0.36f)
                    cubicTo(w * 0.17f, h * 0.20f, w * 0.31f, h * 0.12f, w * 0.43f, h * 0.24f)
                    cubicTo(w * 0.47f, h * 0.28f, w * 0.50f, h * 0.31f, w * 0.50f, h * 0.31f)
                    cubicTo(w * 0.50f, h * 0.31f, w * 0.53f, h * 0.28f, w * 0.57f, h * 0.24f)
                    cubicTo(w * 0.69f, h * 0.12f, w * 0.83f, h * 0.20f, w * 0.83f, h * 0.36f)
                    cubicTo(w * 0.83f, h * 0.55f, w * 0.58f, h * 0.72f, w * 0.50f, h * 0.81f)
                }
                drawPath(path, contentColor, style = stroke)
            }
            NavigationGlyph.Settings -> {
                drawCircle(contentColor, radius = w * 0.27f, center = center, style = stroke)
                drawCircle(surfaceColor, radius = w * 0.08f, center = center)
                for (i in 0..7) {
                    val angle = Math.toRadians(i * 45.0).toFloat()
                    val inner = w * 0.30f
                    val outer = w * 0.42f
                    val innerPoint = androidx.compose.ui.geometry.Offset(
                        x = center.x + kotlin.math.cos(angle) * inner,
                        y = center.y + kotlin.math.sin(angle) * inner,
                    )
                    val outerPoint = androidx.compose.ui.geometry.Offset(
                        x = center.x + kotlin.math.cos(angle) * outer,
                        y = center.y + kotlin.math.sin(angle) * outer,
                    )
                    drawLine(contentColor, innerPoint, outerPoint, strokeWidth, StrokeCap.Round)
                }
            }
        }
    }
}
