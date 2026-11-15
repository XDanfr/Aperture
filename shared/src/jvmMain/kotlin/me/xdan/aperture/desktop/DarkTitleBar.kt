package me.xdan.aperture.desktop

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import me.xdan.aperture.platform.Log
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.RootPaneContainer

/**
 * Aperture is always dark, so its window frame should be too, whatever the system theme.
 *
 * Call [preferDarkWindowAppearance] first thing in main (macOS reads it when AWT starts), then
 * [useDarkTitleBar] on each window. Linux window managers draw their own decorations and follow
 * the desktop theme, so there's nothing to do there.
 */
object DarkTitleBar {
    fun preferDarkWindowAppearance() {
        if (Platform.isMac()) System.setProperty("apple.awt.application.appearance", MAC_DARK_APPEARANCE)
    }

    fun useDarkTitleBar(window: Window) {
        when {
            Platform.isMac() -> (window as? RootPaneContainer)?.rootPane
                ?.putClientProperty("apple.awt.windowAppearance", MAC_DARK_APPEARANCE)
            Platform.isWindows() -> {
                if (window.isDisplayable) setWindowsDarkMode(window)
                else window.addWindowListener(object : WindowAdapter() {
                    override fun windowOpened(e: WindowEvent) {
                        window.removeWindowListener(this)
                        setWindowsDarkMode(window)
                    }
                })
            }
        }
    }

    private fun setWindowsDarkMode(window: Window) {
        runCatching {
            val hwnd = Native.getWindowPointer(window) ?: return
            val enabled = Memory(4).apply { setInt(0, 1) }
            // Attribute 20 since Windows 10 20H1; builds before that used 19 for the same thing.
            val result = Dwmapi.INSTANCE.DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, enabled, 4)
            if (result != 0) Dwmapi.INSTANCE.DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE_OLD, enabled, 4)
        }.onFailure { Log.w(TAG, "Couldn't darken the title bar", it) }
    }

    @Suppress("FunctionName")
    private interface Dwmapi : Library {
        fun DwmSetWindowAttribute(hwnd: Pointer, attribute: Int, value: Pointer, size: Int): Int

        companion object {
            val INSTANCE: Dwmapi by lazy { Native.load("dwmapi", Dwmapi::class.java) }
        }
    }

    private const val TAG = "DarkTitleBar"
    private const val MAC_DARK_APPEARANCE = "NSAppearanceNameDarkAqua"
    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
    private const val DWMWA_USE_IMMERSIVE_DARK_MODE_OLD = 19
}
