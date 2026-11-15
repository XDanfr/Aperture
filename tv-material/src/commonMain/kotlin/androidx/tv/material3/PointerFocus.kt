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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester

/**
 * Not part of upstream TV Material: mouse support for desktop ports.
 *
 * On desktop the mouse moves focus, since focus is how TV components show hover. When the mouse
 * then leaves the item it focused, focus is *parked*: cleared, so nothing looks focused, and
 * remembered, so the next key press puts it back. Never parked on Android.
 */
object PointerFocus {
    /** True while focus is parked. Code that pulls focus back when it's lost should wait. */
    var isParked: Boolean by mutableStateOf(false)
        private set

    private var parkedRequester: FocusRequester? = null

    /** How many [pointerFocusRegion]s the mouse is inside; focus isn't parked while it's > 0. */
    internal var regionsUnderPointer = 0

    /**
     * True while a text field has focus. Hovering doesn't move focus away from it then (only a
     * click does), so typing keeps going to the field wherever the mouse rests.
     */
    var isTextInputFocused: Boolean = false
        private set

    fun onTextInputFocusChanged(focused: Boolean) {
        isTextInputFocused = focused
    }

    internal fun park(requester: FocusRequester) {
        parkedRequester = requester
        isParked = true
    }

    /**
     * Puts focus back where the mouse left it. Returns true when it did, in which case the key
     * press that called this should be consumed, as a TV remote's first press only wakes focus.
     */
    fun restore(): Boolean {
        if (!isParked) return false
        val requester = parkedRequester
        unpark()
        return requester != null && runCatching { requester.requestFocus() }.isSuccess
    }

    /** Something took focus (another hover, a new screen); forget the parked item. */
    fun onFocusTaken() {
        if (isParked) unpark()
    }

    private fun unpark() {
        parkedRequester = null
        isParked = false
    }
}
