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

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Where the mouse last was, in window coordinates. Content that scrolls under a still pointer
 * produces synthetic move and exit events; comparing window positions ignores those, so focus only
 * follows the mouse when the mouse itself moves (otherwise a row could chase its own scrolling).
 */
private object PointerTracker {
    var lastWindowPosition: Offset = Offset.Unspecified

    /** Bumped whenever the mouse focuses an item, so a pending park can tell it's out of date. */
    var focusGeneration = 0L
}

/**
 * How long the mouse may be off an item before its focus is parked. Crossing the gap between two
 * neighbouring items is quicker than this, so focus doesn't flicker off and back on.
 */
private const val PARK_DELAY_MS = 120L

private class PointerFocusState {
    var coordinates: LayoutCoordinates? = null
    var isFocused = false
    /** True while this item's focus came from the mouse, so the mouse leaving should park it. */
    var focusedByPointer = false
    var parkJob: Job? = null
}

@OptIn(ExperimentalComposeUiApi::class)
internal actual fun Modifier.pointerInteraction(
    enabled: Boolean,
    interactionSource: MutableInteractionSource,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
): Modifier = composed {
    val focusRequester = remember { FocusRequester() }
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val currentEnabled by rememberUpdatedState(enabled)
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val state = remember { PointerFocusState() }

    fun focusFromPointer() {
        PointerTracker.focusGeneration++
        state.parkJob?.cancel()
        state.focusedByPointer = true
        PointerFocus.onFocusTaken()
        runCatching { focusRequester.requestFocus() }
    }

    /** The pointer's window position if the mouse itself moved, or null for a synthetic event. */
    fun movedTo(event: PointerEvent): Offset? {
        val local = event.changes.firstOrNull()?.position ?: return null
        val window = state.coordinates?.takeIf { it.isAttached }?.localToWindow(local) ?: return null
        if (window == PointerTracker.lastWindowPosition) return null
        PointerTracker.lastWindowPosition = window
        return window
    }

    this.focusRequester(focusRequester)
        .onFocusChanged { focus ->
            state.isFocused = focus.isFocused
            if (!focus.isFocused) {
                state.focusedByPointer = false
                state.parkJob?.cancel()
            }
        }
        .onGloballyPositioned { state.coordinates = it }
        .onPointerEvent(PointerEventType.Move) { event ->
            // A focused text field keeps focus under a hovering mouse; only a click moves it.
            if (movedTo(event) != null && !PointerFocus.isTextInputFocused) focusFromPointer()
        }
        .onPointerEvent(PointerEventType.Enter) { event ->
            // The mouse arriving counts too (unless content scrolled under a still pointer).
            if (movedTo(event) != null && !PointerFocus.isTextInputFocused) focusFromPointer()
        }
        .onPointerEvent(PointerEventType.Exit) { event ->
            if (movedTo(event) == null || !state.focusedByPointer || !state.isFocused) return@onPointerEvent
            val generation = PointerTracker.focusGeneration
            state.parkJob?.cancel()
            state.parkJob = scope.launch {
                delay(PARK_DELAY_MS)
                if (generation != PointerTracker.focusGeneration || !state.isFocused) return@launch
                if (PointerFocus.regionsUnderPointer > 0) return@launch
                // Park before clearing, so screens that pull focus back see why it went.
                PointerFocus.park(focusRequester)
                focusManager.clearFocus()
            }
        }
        .pointerInput(interactionSource) {
            detectTapGestures(
                onPress = { offset ->
                    if (!currentEnabled) return@detectTapGestures
                    val press = PressInteraction.Press(offset)
                    interactionSource.emit(press)
                    val released = tryAwaitRelease()
                    interactionSource.emit(
                        if (released) PressInteraction.Release(press) else PressInteraction.Cancel(press)
                    )
                },
                onTap = {
                    focusFromPointer()
                    if (currentEnabled) currentOnClick?.invoke()
                },
                onLongPress = {
                    focusFromPointer()
                    if (currentEnabled) currentOnLongClick?.invoke()
                },
            )
        }
}

internal actual fun Modifier.absorbPointerClicks(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.type == PointerEventType.Press || event.type == PointerEventType.Release) {
                event.changes.forEach { it.consume() }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.pointerFocusRegion(): Modifier = composed {
    val inside = remember { booleanArrayOf(false) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { if (inside[0]) PointerFocus.regionsUnderPointer-- }
    }
    this.onPointerEvent(PointerEventType.Enter) {
        if (!inside[0]) { inside[0] = true; PointerFocus.regionsUnderPointer++ }
    }.onPointerEvent(PointerEventType.Exit) {
        if (inside[0]) { inside[0] = false; PointerFocus.regionsUnderPointer-- }
    }
}

actual fun Modifier.textInputFocusTracking(): Modifier = composed {
    val focused = remember { booleanArrayOf(false) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { if (focused[0]) PointerFocus.onTextInputFocusChanged(false) }
    }
    this.onFocusChanged {
        if (it.isFocused != focused[0]) {
            focused[0] = it.isFocused
            PointerFocus.onTextInputFocusChanged(it.isFocused)
        }
    }
}
