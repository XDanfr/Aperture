package me.xdan.aperture.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.focus.FocusRequester

class FocusMemoryManager {
    private val requesters = mutableMapOf<String, FocusRequester>()

    fun getRequester(key: String): FocusRequester {
        return requesters.getOrPut(key) { FocusRequester() }
    }
}

val LocalFocusMemoryManager = staticCompositionLocalOf { FocusMemoryManager() }

@Composable
fun ProvideFocusMemory(content: @Composable () -> Unit) {
    val manager = remember { FocusMemoryManager() }
    CompositionLocalProvider(LocalFocusMemoryManager provides manager) {
        content()
    }
}
