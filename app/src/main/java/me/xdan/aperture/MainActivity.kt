package me.xdan.aperture

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.rememberNavBackStack
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import me.xdan.aperture.ui.navigation.Destination
import me.xdan.aperture.ui.navigation.NavGraph
import me.xdan.aperture.ui.screen.ambient.AmbientMode
import me.xdan.aperture.ui.theme.ApertureTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    private var lastInteractionTime by mutableLongStateOf(System.currentTimeMillis())
    private var isAmbientActive by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ApertureTheme {
                androidx.tv.material3.Surface(
                    modifier = Modifier.fillMaxSize(),
                    colors = androidx.tv.material3.SurfaceDefaults.colors(
                        containerColor = androidx.tv.material3.MaterialTheme.colorScheme.background
                    )
                ) {
                    val backstack = rememberNavBackStack(Destination.Home) as NavBackStack<Destination>

                    LaunchedEffect(Unit) {
                        while (true) {
                            delay(1000)
                            if (System.currentTimeMillis() - lastInteractionTime > 5 * 60 * 1000) {
                                isAmbientActive = true
                            }
                        }
                    }

                    if (isAmbientActive) {
                        AmbientMode()
                    } else {
                        NavGraph(
                            backstack = backstack,
                            onNavigate = {
                                lastInteractionTime = System.currentTimeMillis()
                                backstack.add(it)
                            }
                        )
                    }
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        lastInteractionTime = System.currentTimeMillis()
        if (isAmbientActive) {
            isAmbientActive = false
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
