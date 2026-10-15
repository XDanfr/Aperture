package me.xdan.aperture.ui.theme

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.tv.material3.MaterialTheme
import me.xdan.aperture.ui.theme.tokens.ApertureTokens
import me.xdan.aperture.ui.theme.tokens.LocalApertureTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

class ApertureThemeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun dynamicPaletteSettlesWithoutAnimationFramesAndReusesTokens() {
        val accent = mutableStateOf(Color(0xFFFF7474))
        val unrelated = mutableIntStateOf(0)
        var primary = Color.Unspecified
        var semanticPrimary = Color.Unspecified
        var tokens: ApertureTokens? = null
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val currentAccent = accent.value
            val tick = unrelated.intValue
            ApertureTheme(themeId = "dynamic", dynamicAccent = currentAccent) {
                val materialPrimary = MaterialTheme.colorScheme.primary
                val currentTokens = LocalApertureTokens.current
                SideEffect {
                    check(tick >= 0)
                    primary = materialPrimary
                    semanticPrimary = currentTokens.colorScheme.primary
                    tokens = currentTokens
                }
            }
        }
        compose.runOnIdle { accent.value = Color(0xFF72D4EF) }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {
            assertEquals(accent.value, primary)
            assertEquals(accent.value, semanticPrimary)
        }
        val settledTokens = tokens
        compose.runOnIdle { unrelated.intValue++ }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertSame(settledTokens, tokens) }
    }
}
