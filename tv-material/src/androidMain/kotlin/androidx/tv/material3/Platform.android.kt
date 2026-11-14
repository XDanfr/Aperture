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

import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.text.PlatformTextStyle

internal actual val API_28_OR_ABOVE = Build.VERSION.SDK_INT >= 28

internal actual fun KeyEvent.isBackPress() = key.nativeKeyCode == android.view.KeyEvent.KEYCODE_BACK

internal actual fun KeyEvent.platformRepeatCount(): Int? = nativeKeyEvent.repeatCount

internal actual fun Paint.setGlowLayer(blurRadiusPx: Float, color: Color) {
    val frameworkPaint = asFrameworkPaint()
    frameworkPaint.color = color.copy(alpha = 0f).toArgb()
    frameworkPaint.setShadowLayer(
        /* radius= */ blurRadiusPx,
        /* dx= */ 0f,
        /* dy= */ 0f,
        /* shadowColor= */ color.toArgb(),
    )
}

@Suppress("DEPRECATION")
internal actual val DefaultPlatformTextStyle: PlatformTextStyle? = PlatformTextStyle(includeFontPadding = false)

@androidx.compose.runtime.Composable
internal actual fun rememberAccessibilityManager(): AccessibilityManager {
    val context = androidx.compose.ui.platform.LocalContext.current
    return androidx.compose.runtime.remember {
        val manager = context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE)
            as android.view.accessibility.AccessibilityManager
        object : AccessibilityManager {
            override val isEnabled: Boolean get() = manager.isEnabled
        }
    }
}

internal actual fun androidx.compose.ui.Modifier.absorbPointerClicks(): androidx.compose.ui.Modifier = this

actual fun androidx.compose.ui.Modifier.pointerFocusRegion(): androidx.compose.ui.Modifier = this

actual fun androidx.compose.ui.Modifier.textInputFocusTracking(): androidx.compose.ui.Modifier = this

internal actual fun androidx.compose.ui.Modifier.pointerInteraction(
    enabled: Boolean,
    interactionSource: androidx.compose.foundation.interaction.MutableInteractionSource,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
): androidx.compose.ui.Modifier = this
