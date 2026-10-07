package me.xdan.aperture.ui.component

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class InputControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun pointerClickAndRemoteReleaseActivateOnceAndDisabledPointerDoesNotActivate() {
        var clicks = 0
        val focus = FocusRequester()
        compose.setContent {
            MaterialTheme {
                Column {
                    InputButton(onClick = { clicks++ }, modifier = Modifier.testTag("enabled").focusRequester(focus)) { Text("Play") }
                    InputButton(onClick = { clicks++ }, enabled = false, modifier = Modifier.testTag("disabled")) { Text("Disabled") }
                }
            }
        }
        compose.onNodeWithTag("enabled").performTouchInput { click() }
        compose.onNodeWithTag("disabled").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, clicks); focus.requestFocus() }
        compose.onNodeWithTag("enabled").performKeyInput { keyDown(Key.Enter) }
        compose.runOnIdle { assertEquals(1, clicks) }
        compose.onNodeWithTag("enabled").performKeyInput { keyUp(Key.Enter) }
        compose.runOnIdle { assertEquals(2, clicks) }
    }

    @Test
    fun pointerLongPressDoesNotAlsoClick() {
        var clicks = 0
        var holds = 0
        compose.setContent {
            MaterialTheme {
                InputSurface(onClick = { clicks++ }, onPointerLongClick = { holds++ }, modifier = Modifier.size(100.dp).testTag("card")) { Text("Movie") }
            }
        }
        compose.onNodeWithTag("card").performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(0, clicks); assertEquals(1, holds) }
    }

    @Test
    fun dialogPanelConsumesTapsWhileScrimAndEscapeDismiss() {
        var dismissals = 0
        val focus = FocusRequester()
        compose.setContent {
            MaterialTheme {
                ApertureDialog(onDismissRequest = { dismissals++ }) {
                    Box(Modifier.fillMaxSize().testTag("scrim").onSurfaceTap { dismissals++ }) {
                        Box(Modifier.align(Alignment.CenterEnd).width(180.dp).fillMaxHeight()
                            .onSurfaceTap {}.focusRequester(focus).focusable().testTag("panel"))
                    }
                }
            }
        }
        compose.onNodeWithTag("panel").performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, dismissals) }
        compose.onNodeWithTag("scrim").performTouchInput { click(Offset(5f, centerY)) }
        compose.runOnIdle { assertEquals(1, dismissals); focus.requestFocus() }
        compose.onNodeWithTag("panel").performKeyInput { keyDown(Key.Escape) }
        compose.runOnIdle { assertEquals(1, dismissals) }
        compose.onNodeWithTag("panel").performKeyInput { keyUp(Key.Escape) }
        compose.runOnIdle { assertEquals(2, dismissals) }
    }
}
