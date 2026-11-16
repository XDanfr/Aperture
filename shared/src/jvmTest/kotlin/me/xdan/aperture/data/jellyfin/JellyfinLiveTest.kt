package me.xdan.aperture.data.jellyfin

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.xdan.aperture.companion.CompanionPlatform
import me.xdan.aperture.data.local.AppDatabase
import me.xdan.aperture.data.local.entity.ProfileEntity
import me.xdan.aperture.data.repository.ProfileRepositoryImpl
import me.xdan.aperture.data.repository.UserPreferencesRepositoryImpl
import me.xdan.aperture.platform.PropertiesStores
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import okio.Path.Companion.toOkioPath
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Signs in to a real Jellyfin 12 server, syncs its library and signs out again. Skipped unless
 * APERTURE_JELLYFIN_URL, APERTURE_JELLYFIN_USER and APERTURE_JELLYFIN_PASSWORD are set, so it never
 * runs in CI and no server details live in the repository.
 */
class JellyfinLiveTest {
    private val url = System.getenv("APERTURE_JELLYFIN_URL")
    private val user = System.getenv("APERTURE_JELLYFIN_USER")
    private val password = System.getenv("APERTURE_JELLYFIN_PASSWORD")

    @Test
    fun `signs in, syncs and signs out`() = runBlocking {
        assumeTrue("Set APERTURE_JELLYFIN_* to run against a server", !url.isNullOrBlank() && !user.isNullOrBlank())
        val temp = Files.createTempDirectory("jellyfin-live").toFile()
        val database = Room.databaseBuilder<AppDatabase>(File(temp, "db").absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        try {
            val stores = PropertiesStores(File(temp, "prefs"))
            val credentials = JellyfinCredentials(stores)
            val clients = JellyfinClients(
                createJellyfin { clientInfo = ClientInfo("Aperture", "live-test") },
                credentials,
                object : CompanionPlatform {
                    override val hasLocalNetworkPermission = true
                    override val deviceName = "Aperture live test"
                    override fun localIpv4Address(): String? = null
                    override fun advertise(serviceName: String, port: Int, txt: Map<String, String>) = Unit
                    override fun stopAdvertising() = Unit
                }
            )
            val preferences = UserPreferencesRepositoryImpl(
                PreferenceDataStoreFactory.createWithPath { File(temp, "prefs.preferences_pb").toOkioPath() }
            )
            val profiles = ProfileRepositoryImpl(database.profileDao(), preferences, database.jellyfinDao(), credentials)
            profiles.profiles.first()
            val accounts = JellyfinAccounts(database.jellyfinDao(), clients, credentials, profiles)
            val sync = JellyfinSync(database.jellyfinDao(), clients)

            val account = accounts.signInWithPassword(ProfileEntity.DEFAULT_ID, url!!, user!!, password.orEmpty())
            println("Signed in as ${account.userName} on ${account.serverName} ${account.serverVersion} (admin: ${account.isAdministrator})")

            val started = System.nanoTime()
            val full = sync.fullSync(account)
            val fullMs = (System.nanoTime() - started) / 1_000_000
            val dao = database.jellyfinDao()
            val counts = listOf("MOVIE", "SERIES", "EPISODE", "VIDEO").associateWith { dao.countOfType(account.id, it) }
            val libraries = dao.libraries(account.id).first()
            println("Full sync: ${full.items} items in ${fullMs} ms, $counts")
            println("Libraries: ${libraries.joinToString { "${it.name} (${it.collectionType ?: "mixed"})" }}")
            assertTrue(libraries.isNotEmpty())
            assertEquals(full.items, counts.values.sum())

            // Matching data the hybrid mode will rely on.
            val movies = dao.itemsOfType(account.id, "MOVIE").first()
            if (movies.isNotEmpty()) {
                val withTmdb = movies.count { it.tmdbId != null }
                println("Movies with a TMDB id: $withTmdb of ${movies.size}")
            }

            val synced = dao.account(account.id)!!
            val incrementalStarted = System.nanoTime()
            val incremental = sync.incrementalSync(synced, synced.lastSyncAt!!)
            println("Incremental sync: ${incremental.items} changed items in ${(System.nanoTime() - incrementalStarted) / 1_000_000} ms")

            accounts.signOut(ProfileEntity.DEFAULT_ID)
            assertNull(dao.account(account.id))
            assertEquals(0, dao.countOfType(account.id, "MOVIE"))
            assertNull(credentials.token(account.id))
        } finally {
            database.close()
            temp.deleteRecursively()
        }
    }
}
