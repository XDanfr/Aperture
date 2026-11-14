/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.tv.material3

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.skiaPaint
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.MaskFilter

// Skia always supports blurred draws.
internal actual val API_28_OR_ABOVE = true

internal actual fun KeyEvent.isBackPress() = key == Key.Back || key == Key.Escape

// AWT doesn't expose a repeat count; SurfaceImpl tracks held keys itself.
internal actual fun KeyEvent.platformRepeatCount(): Int? = null

/**
 * Android draws a transparent shape with a shadow layer, which leaves only the blur outside the
 * shape. Skia's OUTER blur draws exactly that region. Android converts a shadow radius to a
 * Gaussian sigma as `radius * 0.57735 + 0.5`; matching it keeps the glow the same size.
 */
internal actual fun Paint.setGlowLayer(blurRadiusPx: Float, color: Color) {
    val skiaPaint = this.skiaPaint
    skiaPaint.color = color.toArgb()
    skiaPaint.maskFilter = if (blurRadiusPx > 0f) {
        MaskFilter.makeBlur(FilterBlurMode.OUTER, blurRadiusPx * 0.57735f + 0.5f)
    } else null
}

// Desktop text has no font padding to remove.
internal actual val DefaultPlatformTextStyle: PlatformTextStyle? = null

private object NoAccessibilityManager : AccessibilityManager {
    override val isEnabled: Boolean = false
}

// Desktop screen readers don't need the carousel to stop auto-scrolling.
@androidx.compose.runtime.Composable
internal actual fun rememberAccessibilityManager(): AccessibilityManager = NoAccessibilityManager
