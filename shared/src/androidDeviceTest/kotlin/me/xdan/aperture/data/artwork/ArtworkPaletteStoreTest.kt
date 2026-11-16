package me.xdan.aperture.data.artwork

import android.graphics.Bitmap
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import me.xdan.aperture.util.ScreenMetrics
import okio.Path.Companion.toOkioPath
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.xdan.aperture.data.local.AppDatabase
import me.xdan.aperture.data.local.entity.ArtworkPaletteEntity
import me.xdan.aperture.data.local.DatabaseMigrations
import me.xdan.aperture.ui.artwork.ArtworkExtractionVersion
import org.junit.Assert.*
import org.junit.Test
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody

class ArtworkPaletteStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val screen = ScreenMetrics { 1920 to 1080 }

    private fun store(db: AppDatabase, loader: () -> ImageLoader) =
        ArtworkPaletteStore(context, db.artworkPaletteDao(), screen, loader)

    @Test
    fun successfulDisplayFetchPersistsTheSeedAndNewStoreInstancesReuseIt() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            lateinit var loader: ImageLoader
            val store = store(db) { loader }
            val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF008577.toInt()) }
            var displayFetches = 0
            loader = ImageLoader.Builder(context).components {
                add(ArtworkPaletteInterceptor(store) { loader })
                add(Interceptor { chain ->
                    displayFetches++
                    SuccessResult(bitmap.asImage(), chain.request, DataSource.MEMORY)
                })
            }.build()
            loader.execute(ImageRequest.Builder(context).data("https://image.tmdb.org/t/p/w500/sample.jpg").build())
            val saved = withTimeout(5_000) {
                var entry = db.artworkPaletteDao().get("/sample.jpg")
                while (entry == null) { delay(20); entry = db.artworkPaletteDao().get("/sample.jpg") }
                entry
            }
            assertEquals(ArtworkExtractionVersion, saved.extractionVersion)
            assertEquals(saved.seedArgb, store(db) { loader }.seed("/sample.jpg"))
            assertEquals(1, displayFetches)
            loader.shutdown()
        } finally { db.close() }
    }

    @Test
    fun decodedArtworkWarmsTheTableWithoutAnotherNetworkFetch() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val cacheDirectory = java.io.File(context.cacheDir, "palette-network-test-${System.nanoTime()}")
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF008577.toInt()) }
        val bytes = java.io.ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bitmap.recycle()
        val fetches = java.util.concurrent.atomic.AtomicInteger()
        val client = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            fetches.incrementAndGet()
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200).message("OK")
                .sentRequestAtMillis(System.currentTimeMillis()).receivedResponseAtMillis(System.currentTimeMillis())
                .header("Content-Type", "image/png")
                .header("Cache-Control", "public, max-age=86400")
                .body(bytes.toResponseBody("image/png".toMediaType())).build()
        }.build()
        lateinit var loader: ImageLoader
        val store = store(db) { loader }
        loader = ImageLoader.Builder(context)
            .diskCache(coil3.disk.DiskCache.Builder().directory(cacheDirectory.toOkioPath()).build())
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { client }))
                add(ArtworkPaletteInterceptor(store) { loader })
            }.build()
        try {
            val result = loader.execute(ImageRequest.Builder(context)
                .data("https://image.tmdb.org/t/p/w500/network-test.png")
                .size(256).allowHardware(true).build())
            assertTrue(result is SuccessResult)
            withTimeout(5_000) {
                while (db.artworkPaletteDao().get("/network-test.png") == null) delay(20)
            }
            assertEquals(1, fetches.get())
        } finally { loader.shutdown(); db.close(); cacheDirectory.deleteRecursively() }
    }

    @Test
    fun extractionVersionInvalidatesAnOldSavedSeed() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            db.artworkPaletteDao().put(ArtworkPaletteEntity("/versioned.jpg", 123, 1))
            val loader = ImageLoader.Builder(context).build()
            val store = store(db) { loader }
            val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFFCC3333.toInt()) }
            val request = ImageRequest.Builder(context).data("https://image.tmdb.org/t/p/w1280/versioned.jpg").build()
            store.artworkLoaded(request.data.toString(), SuccessResult(bitmap.asImage(), request, DataSource.MEMORY), loader)
            withTimeout(5_000) {
                while (db.artworkPaletteDao().get("/versioned.jpg")?.extractionVersion != ArtworkExtractionVersion) delay(20)
            }
            assertNotEquals(123, db.artworkPaletteDao().get("/versioned.jpg")!!.seedArgb)
            loader.shutdown()
        } finally { db.close() }
    }

    @Test
    fun migrationAddsThePaletteTableWithoutLosingTheLibrary() = runBlocking {
        val name = "palette-migration-test.db"
        context.deleteDatabase(name)
        // A version 7 database: the media and progress tables of the time, and no palette table.
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use {
            it.execSQL("CREATE TABLE IF NOT EXISTS `media` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `filePath` TEXT NOT NULL, `sourceRootUri` TEXT, `parentDocumentUri` TEXT, `title` TEXT NOT NULL, `year` INTEGER, `tmdbId` INTEGER, `posterPath` TEXT, `backdropPath` TEXT, `overview` TEXT, `metadataAttemptedAt` INTEGER, `type` TEXT NOT NULL, `seasonNumber` INTEGER, `episodeNumber` INTEGER, `episodeTitle` TEXT, `episodeOverview` TEXT, `stillPath` TEXT, `duration` INTEGER, `lastPlayedPosition` INTEGER, `isFavorite` INTEGER NOT NULL, `favoriteAddedAt` INTEGER, `isHidden` INTEGER NOT NULL, `dateAdded` INTEGER NOT NULL)")
            it.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_media_filePath` ON `media` (`filePath`)")
            it.execSQL("CREATE TABLE IF NOT EXISTS `playback_progress` (`mediaId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `duration` INTEGER NOT NULL, `lastUpdated` INTEGER NOT NULL, `isCompleted` INTEGER NOT NULL, `completedAt` INTEGER, `keepInContinueWatching` INTEGER NOT NULL, PRIMARY KEY(`mediaId`))")
            it.execSQL("INSERT INTO media (id, filePath, title, type, isFavorite, isHidden, dateAdded) VALUES (1, '/saved.mkv', 'Saved library title', 'MOVIE', 0, 0, 1)")
            it.version = 7
        }
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(DatabaseMigrations.MIGRATION_7_8, DatabaseMigrations.MIGRATION_8_9, DatabaseMigrations.MIGRATION_9_10).build()
        try {
            assertEquals("Saved library title", migrated.mediaDao().getMediaById(1, 1)!!.title)
            migrated.artworkPaletteDao().put(ArtworkPaletteEntity("/new.jpg", 42, ArtworkExtractionVersion))
            assertEquals(42, migrated.artworkPaletteDao().get("/new.jpg")!!.seedArgb)
        } finally { migrated.close(); context.deleteDatabase(name) }
    }
}
