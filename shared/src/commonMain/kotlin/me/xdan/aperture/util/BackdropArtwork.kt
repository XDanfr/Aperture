package me.xdan.aperture.util

import me.xdan.aperture.data.remote.api.TmdbApi
import kotlin.math.max

data class BackdropImageSpec(
    val urlSize: String,
    val widthPx: Int,
    val heightPx: Int
)

enum class BackdropImageUsage {
    HOME,
    AMBIENT
}

/** The device's full screen size in pixels; display metrics on Android, the main monitor on desktop. */
fun interface ScreenMetrics {
    fun screenSizePx(): Pair<Int, Int>
}

fun backdropImageSpec(
    screen: ScreenMetrics,
    usage: BackdropImageUsage = BackdropImageUsage.HOME
): BackdropImageSpec {
    val (screenWidth, screenHeight) = screen.screenSizePx()
    val width = screenWidth.coerceAtLeast(1)
    val height = screenHeight.coerceAtLeast(1)

    val isFourK = width >= FOUR_K_WIDTH_THRESHOLD_PX
    val aspectRatio = height.toFloat() / width.toFloat()
    val targetWidth = if (usage == BackdropImageUsage.AMBIENT && isFourK) {
        width
    } else {
        STANDARD_BACKDROP_WIDTH_PX
    }
    val targetHeight = if (usage == BackdropImageUsage.AMBIENT && isFourK) {
        height
    } else {
        max(1, (targetWidth * aspectRatio).toInt())
    }

    return BackdropImageSpec(
        urlSize = if (isFourK) "original" else "w1280",
        widthPx = targetWidth,
        heightPx = targetHeight
    )
}

fun backdropImageUrl(path: String, spec: BackdropImageSpec): String =
    TmdbApi.IMAGE_BASE_URL + spec.urlSize + path

private const val STANDARD_BACKDROP_WIDTH_PX = 1280
private const val FOUR_K_WIDTH_THRESHOLD_PX = 3200
