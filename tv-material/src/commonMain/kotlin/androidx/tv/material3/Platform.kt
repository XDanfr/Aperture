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

// Aperture port: the three places where tv-material 1.1.0 reached into the Android framework.
// Android actuals reproduce the upstream behaviour exactly; other platforms get equivalents.

package androidx.tv.material3

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.PlatformTextStyle

/** Upstream: `Build.VERSION.SDK_INT >= 28` (shadow layers on hardware canvases). */
internal expect val API_28_OR_ABOVE: Boolean

/** Upstream: `KEYCODE_BACK`. Desktop also treats Escape as back. */
internal expect fun KeyEvent.isBackPress(): Boolean

internal fun KeyEvent.isTypeKeyDown() = type == androidx.compose.ui.input.key.KeyEventType.KeyDown

/** The platform's key repeat count, or null when the platform doesn't report one. */
internal expect fun KeyEvent.platformRepeatCount(): Int?

/** Upstream: a transparent paint carrying a framework shadow layer of [color]. */
internal expect fun Paint.setGlowLayer(blurRadiusPx: Float, color: Color)

/** Upstream: `PlatformTextStyle(includeFontPadding = false)`, which only exists on Android. */
internal expect val DefaultPlatformTextStyle: PlatformTextStyle?

/** Upstream: android.view.accessibility.AccessibilityManager (Carousel stops auto-scrolling when enabled). */
internal interface AccessibilityManager {
    val isEnabled: Boolean
}

@androidx.compose.runtime.Composable
internal expect fun rememberAccessibilityManager(): AccessibilityManager

/**
 * Pointer input for TV surfaces. Upstream only reacts to D-pad Enter, which is all Android TV
 * needs, so the Android actual adds nothing. Desktop maps a click to focus + click and mouse
 * movement to focus, so the focus states the design relies on still show.
 */
/**
 * Desktop: a non-clickable Surface (a dialog panel, a menu) takes mouse clicks on its background,
 * so whatever is behind it, like a dialog's scrim that closes on click, doesn't get them.
 * Children still receive clicks first. Nothing on Android.
 */
internal expect fun androidx.compose.ui.Modifier.absorbPointerClicks(): androidx.compose.ui.Modifier

/**
 * Not part of upstream TV Material. Desktop: while the mouse is inside this region, moving off an
 * item doesn't park focus (see [PointerFocus]); it stays on the last hovered item until the mouse
 * leaves the region. For menus and the navigation drawer. Nothing on Android.
 */
expect fun androidx.compose.ui.Modifier.pointerFocusRegion(): androidx.compose.ui.Modifier

/** Not part of upstream TV Material. Marks a text field, so hovering doesn't take focus from it. */
expect fun androidx.compose.ui.Modifier.textInputFocusTracking(): androidx.compose.ui.Modifier

internal expect fun androidx.compose.ui.Modifier.pointerInteraction(
    enabled: Boolean,
    interactionSource: androidx.compose.foundation.interaction.MutableInteractionSource,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
): androidx.compose.ui.Modifier
