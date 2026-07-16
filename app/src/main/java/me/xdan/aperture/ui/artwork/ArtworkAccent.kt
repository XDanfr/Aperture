package me.xdan.aperture.ui.artwork

import android.graphics.Bitmap
import android.graphics.Color

fun extractArtworkAccent(source: Bitmap): Int {
    val bitmap = Bitmap.createScaledBitmap(source, 32, 32, true)
    var red = 0.0
    var green = 0.0
    var blue = 0.0
    var totalWeight = 0.0
    val hsv = FloatArray(3)
    for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
        val pixel = bitmap.getPixel(x, y)
        Color.colorToHSV(pixel, hsv)
        val value = hsv[2]
        val saturation = hsv[1]
        if (Color.alpha(pixel) < 180 || value < 0.14f || value > 0.92f) continue
        val weight = (0.35f + saturation).toDouble()
        red += Color.red(pixel) * weight
        green += Color.green(pixel) * weight
        blue += Color.blue(pixel) * weight
        totalWeight += weight
    }
    if (bitmap !== source) bitmap.recycle()
    if (totalWeight == 0.0) return 0xFFD0BCFF.toInt()
    val raw = Color.rgb(
        (red / totalWeight).toInt().coerceIn(0, 255),
        (green / totalWeight).toInt().coerceIn(0, 255),
        (blue / totalWeight).toInt().coerceIn(0, 255)
    )
    Color.colorToHSV(raw, hsv)
    hsv[1] = hsv[1].coerceAtLeast(0.35f)
    hsv[2] = hsv[2].coerceIn(0.68f, 0.92f)
    return Color.HSVToColor(hsv)
}
