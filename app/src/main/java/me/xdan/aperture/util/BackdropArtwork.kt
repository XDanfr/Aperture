package me.xdan.aperture.util

import android.content.Context
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

fun backdropImageSpec(
    context: Context,
    usage: BackdropImageUsage = BackdropImageUsage.HOME
): BackdropImageSpec {
    val metrics = context.resources.displayMetrics
    val width = metrics.widthPixels.coerceAtLeast(1)
    val height = metrics.heightPixels.coerceAtLeast(1)

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
