package me.xdan.aperture.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.platform.app.InstrumentationRegistry
import me.xdan.aperture.domain.model.AmbientBrandPlacement
import me.xdan.aperture.domain.model.AmbientModeType
import me.xdan.aperture.domain.model.AmbientSettings
import me.xdan.aperture.domain.repository.LibraryPreparationProgress
import me.xdan.aperture.domain.repository.LibraryPreparationStage
import me.xdan.aperture.ui.screen.onboarding.OnboardingScreen
import me.xdan.aperture.ui.screen.settings.AmbientSettingsDialog
import me.xdan.aperture.ui.theme.ApertureTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class OnboardingCustomisationTest {
    @get:Rule val compose = createComposeRule()

    @Before
    fun grantVideoAccess() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO
            else Manifest.permission.READ_EXTERNAL_STORAGE
        val result = instrumentation.uiAutomation.executeShellCommand(
            "pm grant ${instrumentation.targetContext.packageName} $permission"
        )
        android.os.ParcelFileDescriptor.AutoCloseInputStream(result).use { it.readBytes() }
        assertEquals(android.content.pm.PackageManager.PERMISSION_GRANTED,
            instrumentation.targetContext.checkSelfPermission(permission))
    }

    @Test
    fun scanCompletionKeepsThemePickerOpenUntilUserChoosesLearn() {
        val progress = mutableStateOf(LibraryPreparationProgress(stage = LibraryPreparationStage.MATCHING))
        val theme = mutableStateOf("purple")
        var enteredLibrary = 0
        var learned = 0
        compose.setContent {
            ApertureTheme(themeId = theme.value) {
                OnboardingScreen(
                    progress = progress.value,
                    onStartPreparation = {}, onSkip = {}, onComplete = { enteredLibrary++ },
                    onLearn = { learned++ }, selectedThemeId = theme.value,
                    onSelectTheme = { theme.value = it }
                )
            }
        }
        awaitFocused("Skip")
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("Theme").selectWithRemote()
        awaitFocused("Aperture Purple", inDialog = true)
        compose.onNodeWithText("Choose a theme").assertIsDisplayed()
        saveScreenshot("theme-picker")
        compose.onNodeWithText("Artwork Dynamic [PRE]").selectWithRemote()
        compose.runOnIdle { progress.value = LibraryPreparationProgress(stage = LibraryPreparationStage.COMPLETE) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("Choose a theme").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals("dynamic", theme.value)
            assertEquals(0, enteredLibrary)
            assertEquals(0, learned)
        }
        compose.onNodeWithText("Done").selectWithRemote()
        awaitFocused("Theme")
        compose.onNodeWithText("Your library is ready").assertIsDisplayed()
        saveScreenshot("onboarding-ready")
        compose.onNodeWithText("Learn Aperture").selectWithRemote()
        compose.runOnIdle {
            assertEquals(0, enteredLibrary)
            assertEquals(1, learned)
        }
    }

    @Test
    fun completedScanWaitsForGetStartedWithoutStartingTutorial() {
        var enteredLibrary = 0
        var learned = 0
        compose.setContent {
            ApertureTheme {
                OnboardingScreen(
                    progress = LibraryPreparationProgress(stage = LibraryPreparationStage.COMPLETE),
                    onStartPreparation = {}, onSkip = {}, onComplete = { enteredLibrary++ },
                    onLearn = { learned++ }
                )
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle { assertEquals(0, enteredLibrary) }
        compose.onNodeWithText("Get started").selectWithRemote()
        compose.runOnIdle {
            assertEquals(1, enteredLibrary)
            assertEquals(0, learned)
        }
    }

    @Test
    fun ambientSketchReflectsStyleAndClockAndSavesChosenLayout() {
        var saved: AmbientSettings? = null
        compose.setContent {
            ApertureTheme {
                AmbientSettingsDialog(initial = AmbientSettings(), onSave = { saved = it }, onDismiss = {})
            }
        }
        awaitFocused("Cinematic")
        compose.onNodeWithContentDescription("Cinematic, clock hidden").assertIsDisplayed()
        compose.onNodeWithText("Poster wall").selectWithRemote()
        compose.onNodeWithText("Centre icon").selectWithRemote()
        compose.onNodeWithText("Clock").performScrollTo().selectWithRemote()
        compose.onNodeWithContentDescription("Poster wall, clock shown").assertIsDisplayed()
        saveScreenshot("ambient-preview")
        compose.onNodeWithText("Save").selectWithRemote()
        compose.runOnIdle {
            assertEquals(AmbientModeType.POSTER_WALL, saved?.mode)
            assertEquals(AmbientBrandPlacement.CENTRE, saved?.wallBrandPlacement)
            assertTrue(saved?.showClock == true)
        }
    }
    private fun awaitFocused(label: String, inDialog: Boolean = false) {
        val matcher = if (inDialog) hasText(label) and hasAnyAncestor(isDialog()) else hasText(label)
        compose.waitUntil(5_000) {
            runCatching { compose.onNode(matcher).assertIsFocused() }.isSuccess
        }
    }

    private fun saveScreenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        android.os.SystemClock.sleep(250) // Allow Android window fade-outs to finish before capture.
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        java.io.File(instrumentation.targetContext.cacheDir, "$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

}

private fun SemanticsNodeInteraction.selectWithRemote() {
    performSemanticsAction(SemanticsActions.RequestFocus) { it() }
    performKeyInput { pressKey(Key.DirectionCenter) }
}
