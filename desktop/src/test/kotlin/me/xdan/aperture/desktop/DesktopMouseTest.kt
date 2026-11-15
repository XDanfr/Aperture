package me.xdan.aperture.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.PointerFocus
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.pointerFocusRegion
import androidx.tv.material3.textInputFocusTracking
import me.xdan.aperture.platform.AppBuild
import me.xdan.aperture.platform.AppConfig
import me.xdan.aperture.platform.AppPlatform
import me.xdan.aperture.ui.platform.Dialog
import me.xdan.aperture.ui.platform.onBackgroundClick
import me.xdan.aperture.ui.screen.player.seekBarPointerInput
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The desktop mouse behaviours, driven with real pointer events through Compose. */
@OptIn(ExperimentalTestApi::class)
class DesktopMouseTest {
    @get:Rule
    val rule = createComposeRule()

    @Before
    fun desktop() {
        AppBuild.install(AppConfig(AppPlatform.Desktop, "test", 1, false, "", "", false, false, false))
    }

    @Test
    fun dialogClosesOnClickOutsideItsPanelOnly() {
        var dismissed = 0
        var clicked = 0
        rule.setContent {
            Dialog(onDismissRequest = { dismissed++ }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Box(Modifier.fillMaxSize().testTag("scrim")) {
                    Surface(Modifier.size(300.dp).align(Alignment.Center).testTag("panel")) {
                        Column(Modifier.padding(60.dp)) {
                            Button(onClick = { clicked++ }, modifier = Modifier.testTag("button")) { Text("OK") }
                        }
                    }
                }
            }
        }
        rule.onNodeWithTag("panel").performMouseInput { click(Offset(10f, 10f)) }
        rule.waitForIdle()
        assertEquals(0, dismissed, "a click on the panel's background must not close the dialog")

        rule.onNodeWithTag("button").performMouseInput { click(center) }
        rule.waitForIdle()
        assertEquals(1, clicked)
        assertEquals(0, dismissed, "a button's click must not close the dialog")

        rule.onNodeWithTag("scrim").performMouseInput { click(Offset(8f, 8f)) }
        rule.waitForIdle()
        assertEquals(1, dismissed, "a click on the scrim closes the dialog")
    }

    @Test
    fun backgroundClickIgnoresControlsAndDetectsDoubleClicks() {
        var background = 0
        var double = 0
        var control = 0
        rule.setContent {
            Box(Modifier.fillMaxSize().testTag("video").onBackgroundClick(onDoubleClick = { double++ }) { background++ }) {
                Button(onClick = { control++ }, modifier = Modifier.align(Alignment.Center).testTag("control")) { Text("Play") }
            }
        }
        rule.onNodeWithTag("control").performMouseInput { click(center) }
        rule.waitForIdle()
        assertEquals(1, control)
        assertEquals(0, background, "the control's click isn't a background click")

        rule.onNodeWithTag("video").performMouseInput { click(Offset(5f, 5f)) }
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        assertEquals(1, background)

        rule.onNodeWithTag("video").performMouseInput { doubleClick(Offset(5f, 5f)) }
        rule.waitForIdle()
        assertEquals(1, double, "two quick clicks are a double click")
        assertEquals(2, background, "the first click of a double click still counts once")
    }

    @Test
    fun seekBarReportsPressDragReleaseAndHover() {
        val events = mutableListOf<String>()
        rule.setContent {
            Box(Modifier.padding(20.dp)) {
                Box(
                    Modifier.size(400.dp, 24.dp).testTag("bar").seekBarPointerInput(
                        onHover = { events += "hover ${it?.let { f -> "%.2f".format(f) }}" },
                        onScrubStart = { events += "start %.2f".format(it) },
                        onScrub = { events += "scrub %.2f".format(it) },
                        onScrubEnd = { events += "end" }
                    )
                )
            }
        }
        rule.onNodeWithTag("bar").performMouseInput {
            moveTo(Offset(width * 0.5f, height / 2f))
            press()
            moveTo(Offset(width * 0.75f, height / 2f))
            moveTo(Offset(width * 3f, height / 2f)) // dragged past the end
            release()
        }
        rule.waitForIdle()
        assertEquals("hover 0.50", events.first { it.startsWith("hover") && !it.endsWith("null") })
        assertTrue("start 0.50" in events, "press seeks to where it lands: $events")
        assertTrue("scrub 0.75" in events, "dragging scrubs: $events")
        assertTrue("scrub 1.00" in events, "dragging past the end clamps: $events")
        assertEquals("end", events.last { !it.startsWith("hover") })
    }

    @Test
    fun hoverFocusParksWhenTheMouseLeavesAndRegionsHoldIt() {
        rule.setContent {
            Row(Modifier.fillMaxSize().testTag("root"), horizontalArrangement = Arrangement.spacedBy(80.dp)) {
                Button(onClick = {}, modifier = Modifier.padding(40.dp).testTag("loose")) { Text("Loose") }
                Column(Modifier.pointerFocusRegion().padding(40.dp).testTag("menu"), verticalArrangement = Arrangement.spacedBy(40.dp)) {
                    Button(onClick = {}, modifier = Modifier.testTag("menuItem")) { Text("Menu item") }
                    Button(onClick = {}) { Text("Other") }
                }
            }
        }
        rule.onNodeWithTag("loose").performMouseInput { moveTo(center) }
        rule.waitForIdle()
        rule.onNodeWithTag("loose").assertIsFocused()

        rule.onNodeWithTag("root").performMouseInput { moveTo(Offset(2f, 2f)) }
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
        rule.onNodeWithTag("loose").assertIsNotFocused()
        assertTrue(PointerFocus.isParked, "leaving an item parks focus")

        rule.onNodeWithTag("menuItem").performMouseInput { moveTo(center) }
        rule.waitForIdle()
        rule.onNodeWithTag("menuItem").assertIsFocused()
        assertFalse(PointerFocus.isParked)
        // Into the gap between items, still inside the menu region.
        rule.onNodeWithTag("menuItem").performMouseInput { moveTo(Offset(center.x, height + 20f)) }
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
        rule.onNodeWithTag("menuItem").assertIsFocused()
    }

    @Test
    fun hoveringDoesNotTakeFocusFromATextFieldButClickingDoes() {
        rule.setContent {
            var text by remember { mutableStateOf("") }
            Column(Modifier.fillMaxSize().padding(40.dp), verticalArrangement = Arrangement.spacedBy(40.dp)) {
                BasicTextField(text, { text = it }, Modifier.size(300.dp, 40.dp).textInputFocusTracking().testTag("field"))
                Button(onClick = {}, modifier = Modifier.testTag("button")) { Text("Result") }
            }
        }
        rule.onNodeWithTag("field").performMouseInput { click(center) }
        rule.waitForIdle()
        rule.onNodeWithTag("field").assertIsFocused()

        rule.onNodeWithTag("button").performMouseInput { moveTo(center) }
        rule.waitForIdle()
        rule.onNodeWithTag("field").assertIsFocused()

        rule.onNodeWithTag("button").performMouseInput { click(center) }
        rule.waitForIdle()
        rule.onNodeWithTag("button").assertIsFocused()
        assertFalse(PointerFocus.isTextInputFocused)
    }
}
