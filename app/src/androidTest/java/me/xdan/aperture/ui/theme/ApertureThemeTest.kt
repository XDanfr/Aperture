package me.xdan.aperture.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.tv.material3.MaterialTheme
import me.xdan.aperture.ui.theme.tokens.ApertureTokens
import me.xdan.aperture.ui.theme.tokens.LocalApertureTokens
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ApertureThemeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun colourFadeKeepsTokensStableAndSkipsUnrelatedContent() {
        val accent = mutableStateOf(Color(0xFFCC3333))
        var primary = Color.Unspecified
        var semanticPrimary = Color.Unspecified
        var tokens: ApertureTokens? = null
        var unrelatedCompositions = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ApertureTheme(themeId = "dynamic", dynamicAccent = accent.value) {
                UnrelatedProbe { unrelatedCompositions++ }
                ColourProbe { material, semantic, current ->
                    primary = material
                    semanticPrimary = semantic
                    tokens = current
                }
            }
        }
        val originalTokens = tokens
        val originalColour = primary
        val initialCompositions = unrelatedCompositions
        val targetSeed = Color(0xFF008577)
        val targetColour = dynamicArtworkColorScheme(targetSeed).primary
        compose.runOnIdle { accent.value = targetSeed }
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle {
            assertNotEquals(originalColour, primary)
            assertNotEquals(targetColour, primary)
            assertSame(originalTokens, tokens)
        }
        val duringFadeCompositions = unrelatedCompositions
        assertTrue(duringFadeCompositions <= initialCompositions + 1)
        compose.mainClock.advanceTimeBy(800)
        compose.runOnIdle {
            assertEquals(targetColour, primary)
            assertEquals(targetColour, semanticPrimary)
            assertSame(originalTokens, tokens)
            assertEquals(duringFadeCompositions, unrelatedCompositions)
        }
    }
}

@Composable
private fun UnrelatedProbe(composed: () -> Unit) {
    ApertureTheme.spacing
    SideEffect(composed)
}

@Composable
private fun ColourProbe(colours: (Color, Color, ApertureTokens) -> Unit) {
    val material = MaterialTheme.colorScheme.primary
    val tokens = LocalApertureTokens.current
    val semantic = tokens.colorScheme.primary
    SideEffect { colours(material, semantic, tokens) }
}
