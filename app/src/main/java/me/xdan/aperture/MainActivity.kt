package me.xdan.aperture

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import me.xdan.aperture.companion.CompanionHub
import me.xdan.aperture.ui.ApertureApp
import me.xdan.aperture.ui.ApertureAppState
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val companionHub: CompanionHub by inject()
    private lateinit var appState: ApertureAppState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appState = ApertureAppState(get())

        enableEdgeToEdge()
        setContent {
            ApertureApp(appState = appState, onExit = ::finish)
        }
    }

    override fun onStart() {
        super.onStart()
        companionHub.start()
    }

    override fun onStop() {
        companionHub.stop()
        super.onStop()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (appState.onKeyDown(keyCode.toLong(), isBack = keyCode == KeyEvent.KEYCODE_BACK)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (appState.onKeyUp(keyCode.toLong())) return true
        return super.onKeyUp(keyCode, event)
    }
}

