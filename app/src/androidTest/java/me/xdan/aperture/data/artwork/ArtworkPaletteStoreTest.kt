package me.xdan.aperture.data.artwork

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import coil.ImageLoader
import coil.decode.DataSource
import coil.intercept.Interceptor
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.xdan.aperture.data.local.AppDatabase
import me.xdan.aperture.data.local.entity.ArtworkPaletteEntity
import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.di.DatabaseModule
import me.xdan.aperture.ui.artwork.ArtworkExtractionVersion
import org.junit.Assert.*
import org.junit.Test
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody

class ArtworkPaletteStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun successfulDisplayFetchPersistsTheSeedAndNewStoreInstancesReuseIt() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val store = ArtworkPaletteStore(context, db.artworkPaletteDao())
            val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF008577.toInt()) }
            var displayFetches = 0
            lateinit var loader: ImageLoader
            loader = ImageLoader.Builder(context).components {
                add(ArtworkPaletteInterceptor(store) { loader })
                add(Interceptor { chain ->
                    displayFetches++
                    SuccessResult(BitmapDrawable(context.resources, bitmap), chain.request, DataSource.MEMORY)
                })
            }.build()
            loader.execute(ImageRequest.Builder(context).data("https://image.tmdb.org/t/p/w500/sample.jpg").build())
            val saved = withTimeout(5_000) {
                var entry = db.artworkPaletteDao().get("/sample.jpg")
                while (entry == null) { delay(20); entry = db.artworkPaletteDao().get("/sample.jpg") }
                entry
            }
            assertEquals(ArtworkExtractionVersion, saved.extractionVersion)
            assertEquals(saved.seedArgb, ArtworkPaletteStore(context, db.artworkPaletteDao()).seed("/sample.jpg"))
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
        val store = ArtworkPaletteStore(context, db.artworkPaletteDao())
        lateinit var loader: ImageLoader
        loader = ImageLoader.Builder(context).callFactory(client)
            .diskCache(coil.disk.DiskCache.Builder().directory(cacheDirectory).build())
            .components { add(ArtworkPaletteInterceptor(store) { loader }) }.build()
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
            val store = ArtworkPaletteStore(context, db.artworkPaletteDao())
            val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFFCC3333.toInt()) }
            val request = ImageRequest.Builder(context).data("https://image.tmdb.org/t/p/w1280/versioned.jpg").build()
            val loader = ImageLoader.Builder(context).build()
            store.artworkLoaded(request.data.toString(), SuccessResult(BitmapDrawable(context.resources, bitmap), request, DataSource.MEMORY), loader)
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
        val old = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        old.mediaDao().insertMedia(MediaEntity(id = 1, filePath = "/saved.mkv", title = "Saved library title", type = "MOVIE"))
        old.close()
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("DROP TABLE artwork_palettes")
            it.version = 7
        }
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(DatabaseModule.MIGRATION_7_8).build()
        try {
            assertEquals("Saved library title", migrated.mediaDao().getMediaById(1)!!.title)
            migrated.artworkPaletteDao().put(ArtworkPaletteEntity("/new.jpg", 42, ArtworkExtractionVersion))
            assertEquals(42, migrated.artworkPaletteDao().get("/new.jpg")!!.seedArgb)
        } finally { migrated.close(); context.deleteDatabase(name) }
    }
}
