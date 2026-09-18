package me.xdan.aperture.util

import android.content.Context
import me.xdan.aperture.data.remote.api.TmdbApi
import kotlin.math.max

data class BackdropImageSpec(
    val urlSize: String,
    val widthPx: Int,
    val heightPx: Int
)

fun backdropImageSpec(context: Context): BackdropImageSpec {
    val metrics = context.resources.displayMetrics
    val width = metrics.widthPixels.coerceAtLeast(1)
    val height = metrics.heightPixels.coerceAtLeast(1)

    return if (width >= FOUR_K_WIDTH_THRESHOLD_PX) {
        BackdropImageSpec(
            urlSize = "original",
            widthPx = width,
            heightPx = height
        )
    } else {
        val aspectRatio = height.toFloat() / width.toFloat()
        val targetWidth = STANDARD_BACKDROP_WIDTH_PX
        BackdropImageSpec(
            urlSize = "w1280",
            widthPx = targetWidth,
            heightPx = max(1, (targetWidth * aspectRatio).toInt())
        )
    }
}

fun backdropImageUrl(path: String, spec: BackdropImageSpec): String =
    TmdbApi.IMAGE_BASE_URL + spec.urlSize + path

private const val STANDARD_BACKDROP_WIDTH_PX = 1280
private const val FOUR_K_WIDTH_THRESHOLD_PX = 3200
