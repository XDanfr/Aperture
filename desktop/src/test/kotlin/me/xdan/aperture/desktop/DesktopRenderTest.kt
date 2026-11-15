package me.xdan.aperture.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import me.xdan.aperture.data.local.AppDatabase
import me.xdan.aperture.di.desktopModule
import me.xdan.aperture.di.sharedModule
import me.xdan.aperture.platform.AppBuild
import me.xdan.aperture.platform.AppConfig
import me.xdan.aperture.platform.AppPlatform
import me.xdan.aperture.platform.KeyValueStores
import me.xdan.aperture.platform.PropertiesStores
import me.xdan.aperture.ui.ApertureApp
import me.xdan.aperture.ui.ApertureAppState
import okio.Path.Companion.toOkioPath
import org.jetbrains.skia.EncodedImageFormat
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Boots the real shared app (Koin graph, Room, DataStore) offscreen at a 1080p TV's 960×540 dp
 * and renders it, so the desktop UI can be checked without a display. Frames land in
 * desktop/build/render/.
 */
class DesktopRenderTest {
    private val temp = Files.createTempDirectory("aperture-render").toFile()

    @AfterTest
    fun tearDown() {
        stopKoin()
        temp.deleteRecursively()
    }

    @Test
    fun rendersFirstLaunch() {
        val koin = boot()
        renderFrames(koin, "first-launch", listOf(500L, 6_000L))
        assertTrue(File("build/render/first-launch-6000ms.png").length() > 10_000)
    }

    @Test
    fun rendersLibrary() {
        val koin = boot()
        kotlinx.coroutines.runBlocking {
            koin.get<me.xdan.aperture.domain.repository.UserPreferencesRepository>().setOnboardingCompleted(true)
            val dao = koin.get<me.xdan.aperture.data.local.dao.MediaDao>()
            listOf("Arrival", "Blade Runner 2049", "Dune", "Interstellar", "Moon", "Sunshine").forEachIndexed { i, title ->
                dao.insertMedia(
                    me.xdan.aperture.data.local.entity.MediaEntity(
                        filePath = "/videos/$title.mkv", title = title, year = 2010 + i, type = "MOVIE",
                        overview = "A film used to check the desktop layout.", metadataAttemptedAt = System.currentTimeMillis()
                    )
                )
            }
            dao.insertMedia(
                me.xdan.aperture.data.local.entity.MediaEntity(
                    filePath = "/videos/Severance S01E01.mkv", title = "Severance", type = "EPISODE",
                    seasonNumber = 1, episodeNumber = 1, metadataAttemptedAt = System.currentTimeMillis()
                )
            )
        }
        renderFrames(koin, "library", listOf(8_000L, 20_000L))
        assertTrue(File("build/render/library-20000ms.png").length() > 10_000)
    }

    @Test
    fun sidebarFollowsTheMouse() {
        val koin = boot()
        seedLibrary(koin)
        val state = ApertureAppState(koin.get())
        val out = File("build/render").apply { mkdirs() }
        val scene = onEdt { ImageComposeScene(1920, 1080, density = Density(2f)) { ApertureApp(appState = state, onExit = {}) } }
        var time = 0L
        fun advance(ms: Long) {
            val until = time + ms * 1_000_000
            while (time < until) {
                time += 16_000_000
                val t = time
                onEdt { scene.render(t) }
                Thread.sleep(5)
            }
        }
        fun snapshot(name: String) {
            val t = time
            val png = onEdt { scene.render(t) }.encodeToData(EncodedImageFormat.PNG)!!.bytes
            File(out, "$name.png").writeBytes(png)
        }
        fun move(x: Float, y: Float) {
            val t = time
            onEdt {
                scene.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Move, androidx.compose.ui.geometry.Offset(x, y), timeMillis = t / 1_000_000)
            }
        }
        try {
            advance(8_000)
            move(1200f, 700f) // over the page
            advance(1_000)
            snapshot("mouse-page")
            move(40f, 500f) // onto the sidebar
            advance(300)
            move(60f, 520f)
            advance(1_500)
            snapshot("mouse-sidebar")
            move(1200f, 700f) // back off it
            advance(1_500)
            snapshot("mouse-left-sidebar")
        } finally {
            onEdt { scene.close() }
        }
        assertTrue(File(out, "mouse-sidebar.png").length() > 10_000)
    }

    private fun <T> onEdt(block: () -> T): T {
        var result: Result<T>? = null
        javax.swing.SwingUtilities.invokeAndWait { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    private fun seedLibrary(koin: org.koin.core.Koin) = kotlinx.coroutines.runBlocking {
        koin.get<me.xdan.aperture.domain.repository.UserPreferencesRepository>().setOnboardingCompleted(true)
        val dao = koin.get<me.xdan.aperture.data.local.dao.MediaDao>()
        listOf("Arrival", "Blade Runner 2049", "Dune", "Interstellar", "Moon", "Sunshine").forEachIndexed { i, title ->
            dao.insertMedia(
                me.xdan.aperture.data.local.entity.MediaEntity(
                    filePath = "/videos/$title.mkv", title = title, year = 2010 + i, type = "MOVIE",
                    overview = "A film used to check the desktop layout.", metadataAttemptedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private fun boot(): org.koin.core.Koin {
        AppBuild.install(
            AppConfig(AppPlatform.Desktop, "test", 1, false, "", "", false, false, false)
        )
        val isolated = module {
            single<KeyValueStores> { PropertiesStores(File(temp, "preferences")) }
            single<DataStore<Preferences>> {
                PreferenceDataStoreFactory.createWithPath { File(temp, "user_prefs.preferences_pb").toOkioPath() }
            }
            single<AppDatabase> {
                Room.databaseBuilder<AppDatabase>(File(temp, "db").absolutePath)
                    .setDriver(BundledSQLiteDriver())
                    .setQueryCoroutineContext(Dispatchers.IO)
                    .build()
            }
        }
        return startKoin {
            allowOverride(true)
            modules(sharedModule, desktopModule, isolated)
        }.koin
    }

    private fun renderFrames(koin: org.koin.core.Koin, name: String, times: List<Long>) {
        val state = ApertureAppState(koin.get())
        val out = File("build/render").apply { mkdirs() }

        // Compose and Lifecycle expect the UI thread, which on desktop is the Swing EDT.
        fun <T> onEdt(block: () -> T): T {
            var result: Result<T>? = null
            javax.swing.SwingUtilities.invokeAndWait { result = runCatching(block) }
            return result!!.getOrThrow()
        }
        val scene = onEdt {
            ImageComposeScene(1920, 1080, density = Density(2f)) {
                ApertureApp(appState = state, onExit = {})
            }
        }
        try {
            var time = 0L
            times.forEach { at ->
                while (time < at * 1_000_000) {
                    time += 16_000_000
                    val frameTime = time
                    onEdt { scene.render(frameTime) }
                    Thread.sleep(5)
                }
                val frameTime = time
                val image = onEdt { scene.render(frameTime) }
                val png = image.encodeToData(EncodedImageFormat.PNG)!!.bytes
                File(out, "$name-${at}ms.png").writeBytes(png)
            }
        } finally {
            onEdt { scene.close() }
        }
    }
}
