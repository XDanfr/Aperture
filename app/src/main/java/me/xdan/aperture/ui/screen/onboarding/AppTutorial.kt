package me.xdan.aperture.ui.screen.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.*
import kotlinx.coroutines.delay
import me.xdan.aperture.ui.navigation.Destination

private data class TutorialStep(
    val title: String,
    val body: String,
    val destination: Destination,
    val showExampleDetails: Boolean = false,
    val placement: TutorialPlacement = TutorialPlacement.BottomEnd,
    val pointer: ImageVector = Icons.Rounded.ArrowUpward
)

private enum class TutorialPlacement { BottomStart, BottomEnd, CenterEnd }

@Composable
fun AppTutorial(
    exampleTitle: String?,
    onNavigate: (Destination) -> Unit,
    onShowExample: (Boolean) -> Unit,
    onFinish: () -> Unit
) {
    val steps = remember(exampleTitle) {
        buildList {
            add(TutorialStep("Meet Spotlight", "Spotlight starts with something you can continue, then suggests other titles from your library.", Destination.Home))
            add(TutorialStep("The sidebar", "Press Left to open the sidebar, then Right to return exactly where you were.", Destination.Home, placement = TutorialPlacement.CenterEnd, pointer = Icons.Rounded.ArrowBack))
            add(TutorialStep("Movies, A–Z", "Movies contains your complete film library in alphabetical order.", Destination.Movies))
            add(TutorialStep("Shows and episodes", "TV Shows keeps everything A–Z, grouped into shows, seasons and episodes.", Destination.Shows))
            if (exampleTitle != null) {
                add(TutorialStep("Build My List", "Open a title like $exampleTitle and choose Add to My List. Your newest additions appear first.", Destination.Home, true, TutorialPlacement.BottomStart, Icons.Rounded.ArrowForward))
            }
            add(TutorialStep("Make it yours", "Settings lives on the sidebar. Here lie themes, library tools, update checking and more.", Destination.Home, placement = TutorialPlacement.CenterEnd, pointer = Icons.Rounded.ArrowBack))
        }
    }
    var index by remember { mutableIntStateOf(0) }
    val step = steps[index]
    val nextRequester = remember { FocusRequester() }

    LaunchedEffect(index) {
        onNavigate(step.destination)
        onShowExample(step.showExampleDetails)
        delay(140)
        runCatching { nextRequester.requestFocus() }
    }

    Popup(
        alignment = Alignment.TopStart,
        properties = PopupProperties(focusable = true, dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = when (step.placement) {
                TutorialPlacement.BottomStart -> Alignment.BottomStart
                TutorialPlacement.BottomEnd -> Alignment.BottomEnd
                TutorialPlacement.CenterEnd -> Alignment.CenterEnd
            }
        ) {
            Surface(
                modifier = Modifier.width(540.dp).padding(32.dp),
                shape = RoundedCornerShape(28.dp),
                colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(horizontal = 36.dp, vertical = 28.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(step.pointer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text("${index + 1} / ${steps.size}", color = MaterialTheme.colorScheme.primary)
                    }
                    AnimatedContent(
                        targetState = index,
                        transitionSpec = {
                            (slideInHorizontally { it / 4 } + fadeIn()) togetherWith
                                (slideOutHorizontally { -it / 4 } + fadeOut())
                        },
                        label = "tutorialStep"
                    ) { current ->
                        Column {
                            Text(steps[current].title, style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.height(8.dp))
                            Text(steps[current].body, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (index == 0) {
                            OutlinedButton(onClick = { onShowExample(false); onFinish() }) { Text("Skip") }
                        } else {
                            OutlinedButton(onClick = { index -= 1 }) { Text("Back") }
                        }
                        Button(
                            onClick = {
                                if (index == steps.lastIndex) {
                                    onShowExample(false)
                                    onFinish()
                                } else index += 1
                            },
                            modifier = Modifier.focusRequester(nextRequester)
                        ) { Text(if (index == steps.lastIndex) "Finish" else "Next") }
                    }
                }
            }
        }
    }
}
