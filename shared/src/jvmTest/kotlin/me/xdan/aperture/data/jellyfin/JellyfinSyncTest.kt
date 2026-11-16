package me.xdan.aperture.data.jellyfin

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import okio.Path.Companion.toOkioPath
import me.xdan.aperture.companion.CompanionPlatform
import me.xdan.aperture.data.local.AppDatabase
import me.xdan.aperture.data.local.entity.JellyfinAccountEntity
import me.xdan.aperture.platform.PropertiesStores
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.api.LibraryUpdateInfo
import org.jellyfin.sdk.model.api.UserDataChangeInfo
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.UUID

/** Sync against a fake Jellyfin: paging, libraries, deletions, incremental and WebSocket updates. */
class JellyfinSyncTest {
    private val temp: File = Files.createTempDirectory("jellyfin-sync").toFile()
    private val server = MockWebServer()
    private val fake = FakeJellyfin()
    private lateinit var database: AppDatabase
    private lateinit var sync: JellyfinSync
    private lateinit var account: JellyfinAccountEntity
    private var now = 1_000_000_000_000L

    @Before
    fun setUp() = runBlocking {
        server.dispatcher = fake
        server.start()
        database = Room.databaseBuilder<AppDatabase>(File(temp, "db").absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        val credentials = JellyfinCredentials(PropertiesStores(File(temp, "prefs")))
        val clients = JellyfinClients(
            createJellyfin { clientInfo = ClientInfo("Aperture", "test") },
            credentials,
            object : CompanionPlatform {
                override val hasLocalNetworkPermission = true
                override val deviceName = "Test TV"
                override fun localIpv4Address(): String? = null
                override fun advertise(serviceName: String, port: Int, txt: Map<String, String>) = Unit
                override fun stopAdvertising() = Unit
            }
        )
        val id = database.jellyfinDao().insertAccount(
            JellyfinAccountEntity(
                profileId = 1, serverId = "server", serverName = "Fake", baseUrl = server.url("/").toString().trimEnd('/'),
                userId = JellyfinIds.of(FakeJellyfin.USER), userName = "viewer"
            )
        )
        credentials.saveToken(id, "token")
        account = database.jellyfinDao().account(id)!!
        sync = JellyfinSync(database.jellyfinDao(), clients) { now }
    }

    @After
    fun tearDown() {
        database.close()
        server.close()
        temp.deleteRecursively()
    }

    @Test
    fun `full sync pages through each library and keeps what the index needs`() = runBlocking {
        repeat(1_200) { fake.addMovie("Movie $it") }
        val show = fake.addSeries("Show")
        fake.addEpisode(show, season = 1, episode = 2, title = "Second")
        fake.addMusicAlbum("Not indexed")
        val clip = fake.addHomeVideo("Birthday")

        val result = sync.fullSync(account)

        assertEquals(1_203, result.items)
        val dao = database.jellyfinDao()
        assertEquals(1_200, dao.countOfType(account.id, "MOVIE"))
        assertEquals(1, dao.countOfType(account.id, "SERIES"))
        val episode = dao.episodesOf(account.id, JellyfinIds.of(show)).single()
        assertEquals(1, episode.seasonNumber)
        assertEquals(2, episode.episodeNumber)
        assertEquals("Show", episode.seriesName)
        assertEquals(FakeJellyfin.SHOWS_VIEW.let(JellyfinIds::of), episode.libraryId)
        val movie = dao.itemsOfType(account.id, "MOVIE").first().first()
        assertEquals("603", movie.tmdbId)
        assertEquals("tt0133093", movie.imdbId)
        assertEquals(2 * 60 * 60 * 1000L, movie.runtimeMs)
        assertEquals(JellyfinIds.of(FakeJellyfin.MOVIES_VIEW), movie.libraryId)
        // Home videos are indexed in their own library.
        val homeVideo = dao.item(account.id, JellyfinIds.of(clip))!!
        assertEquals("VIDEO", homeVideo.type)
        assertEquals(JellyfinIds.of(FakeJellyfin.HOME_VIEW), homeVideo.libraryId)
        // Every library is listed for the sidebar, music included, though music isn't indexed.
        assertEquals(4, dao.libraries(account.id).first().size)
        assertTrue(fake.itemRequests > 3)
    }

    @Test
    fun `a full sync removes what the server no longer has`() = runBlocking {
        val keep = fake.addMovie("Keep")
        val gone = fake.addMovie("Gone")
        sync.fullSync(account)
        fake.remove(gone)
        now += 60_000

        val result = sync.fullSync(account)

        assertEquals(1, result.removed)
        assertTrue(database.jellyfinDao().item(account.id, JellyfinIds.of(keep)) != null)
        assertNull(database.jellyfinDao().item(account.id, JellyfinIds.of(gone)))
    }

    @Test
    fun `incremental sync asks only for changes and keeps everything else`() = runBlocking {
        val old = fake.addMovie("Old")
        sync.fullSync(account)
        now += 60 * 60 * 1000
        val new = fake.addMovie("New")
        fake.markPlayed(old)
        val synced = database.jellyfinDao().account(account.id)!!

        sync.sync(synced)

        val dao = database.jellyfinDao()
        assertTrue(dao.item(account.id, JellyfinIds.of(old))!!.played)
        assertTrue(dao.item(account.id, JellyfinIds.of(new)) != null)
        assertTrue("incremental requests filter by date", fake.sawDateFilter)
    }

    @Test
    fun `websocket messages update the index directly`() = runBlocking {
        val movie = fake.addMovie("Movie")
        val removed = fake.addMovie("Removed")
        sync.fullSync(account)
        val added = fake.addMovie("Added")

        sync.applyLibraryChange(
            account,
            LibraryUpdateInfo(
                foldersAddedTo = emptyList(), foldersRemovedFrom = emptyList(),
                itemsAdded = listOf(JellyfinIds.of(added)), itemsRemoved = listOf(JellyfinIds.of(removed)),
                itemsUpdated = emptyList(), collectionFolders = emptyList(), isEmpty = false
            )
        )
        sync.applyUserDataChange(
            account,
            UserDataChangeInfo(
                userId = FakeJellyfin.USER,
                userDataList = listOf(
                    UserItemDataDto(
                        playbackPositionTicks = 600_000_000, playCount = 1, isFavorite = true,
                        played = false, key = "k", itemId = movie
                    )
                )
            )
        )

        val dao = database.jellyfinDao()
        assertNull(dao.item(account.id, JellyfinIds.of(removed)))
        assertEquals(JellyfinIds.of(FakeJellyfin.MOVIES_VIEW), dao.item(account.id, JellyfinIds.of(added))?.libraryId)
        val updated = dao.item(account.id, JellyfinIds.of(movie))!!
        assertTrue(updated.isFavorite)
        assertFalse(updated.played)
        assertEquals(60_000L, updated.playbackPositionMs)
    }

    @Test
    fun `a missing token means signed out rather than a crash`() = runBlocking {
        JellyfinCredentials(PropertiesStores(File(temp, "prefs"))).forget(account.id)
        val fresh = JellyfinSync(
            database.jellyfinDao(),
            JellyfinClients(
                createJellyfin { clientInfo = ClientInfo("Aperture", "test") },
                JellyfinCredentials(PropertiesStores(File(temp, "prefs"))),
                object : CompanionPlatform {
                    override val hasLocalNetworkPermission = true
                    override val deviceName = "Test TV"
                    override fun localIpv4Address(): String? = null
                    override fun advertise(serviceName: String, port: Int, txt: Map<String, String>) = Unit
                    override fun stopAdvertising() = Unit
                }
            )
        )
        val error = runCatching { fresh.fullSync(account) }.exceptionOrNull()
        assertTrue(error is JellyfinSync.NotSignedIn)
    }

    @Test
    fun `the watcher syncs only when the active profile uses Jellyfin`() = runBlocking<Unit> {
        fake.addMovie("Movie")
        val preferences = me.xdan.aperture.data.repository.UserPreferencesRepositoryImpl(
            androidx.datastore.preferences.core.PreferenceDataStoreFactory.createWithPath {
                File(temp, "watch.preferences_pb").toOkioPath()
            }
        )
        val credentials = JellyfinCredentials(PropertiesStores(File(temp, "prefs")))
        val profiles = me.xdan.aperture.data.repository.ProfileRepositoryImpl(
            database.profileDao(), preferences, database.jellyfinDao(), credentials
        )
        profiles.profiles.first()
        val clients = JellyfinClients(
            createJellyfin { clientInfo = ClientInfo("Aperture", "test") },
            credentials,
            object : CompanionPlatform {
                override val hasLocalNetworkPermission = true
                override val deviceName = "Test TV"
                override fun localIpv4Address(): String? = null
                override fun advertise(serviceName: String, port: Int, txt: Map<String, String>) = Unit
                override fun stopAdvertising() = Unit
            }
        )
        val accounts = JellyfinAccounts(database.jellyfinDao(), clients, credentials, profiles)
        // Its own sync on the same clients, so forgetting the token below is seen by the sync.
        val watcher = JellyfinLibraryWatcher(accounts, profiles, JellyfinSync(database.jellyfinDao(), clients), database.jellyfinDao())
        val job = launch(Dispatchers.Default) { watcher.run() }
        try {
            // Profile 1 is local, so nothing syncs even though it has an account.
            kotlinx.coroutines.delay(500)
            assertEquals(JellyfinLibraryWatcher.State.Idle, watcher.state.value)
            assertEquals(0, database.jellyfinDao().countOfType(account.id, "MOVIE"))

            profiles.setLibraryMode(1, me.xdan.aperture.domain.model.LibraryMode.JELLYFIN)
            kotlinx.coroutines.withTimeout(10_000) {
                watcher.state.first { it is JellyfinLibraryWatcher.State.Synced }
            }
            assertEquals(1, database.jellyfinDao().countOfType(account.id, "MOVIE"))

            // Losing the token (e.g. a restored backup) reads as signed out, not as a failure.
            profiles.setLibraryMode(1, me.xdan.aperture.domain.model.LibraryMode.LOCAL)
            kotlinx.coroutines.withTimeout(5_000) { watcher.state.first { it == JellyfinLibraryWatcher.State.Idle } }
            credentials.forget(account.id)
            clients.forget(account.id)
            profiles.setLibraryMode(1, me.xdan.aperture.domain.model.LibraryMode.HYBRID)
            kotlinx.coroutines.withTimeout(10_000) {
                watcher.state.first { it == JellyfinLibraryWatcher.State.SignedOut }
            }
        } finally {
            job.cancel()
        }
    }

    @Test
    fun `server addresses are normalised and old servers refused`() {
        assertEquals("http://192.0.2.1:8096", JellyfinAccounts.normalise(" 192.0.2.1:8096/ "))
        assertEquals("https://media.example.com", JellyfinAccounts.normalise("https://media.example.com/web/"))
        assertTrue(JellyfinAccounts.isSupported("12.1.0"))
        assertFalse(JellyfinAccounts.isSupported("10.11.11"))
        assertFalse(JellyfinAccounts.isSupported(""))
    }
}

/** Just enough of Jellyfin's /UserViews and /Items for the sync. */
private class FakeJellyfin : Dispatcher() {
    data class Item(
        val id: UUID, val type: String, val name: String, val view: UUID,
        val seriesId: UUID? = null, val season: Int? = null, val episode: Int? = null,
        var played: Boolean = false, var userDataChanged: Boolean = false, val created: Int
    )

    private val items = mutableListOf<Item>()
    var itemRequests = 0
    var sawDateFilter = false

    fun addMovie(name: String) = add(Item(UUID.randomUUID(), "Movie", name, MOVIES_VIEW, created = items.size))
    fun addSeries(name: String) = add(Item(UUID.randomUUID(), "Series", name, SHOWS_VIEW, created = items.size))
    fun addEpisode(series: UUID, season: Int, episode: Int, title: String) =
        add(Item(UUID.randomUUID(), "Episode", title, SHOWS_VIEW, series, season, episode, created = items.size))
    fun addHomeVideo(name: String) = add(Item(UUID.randomUUID(), "Video", name, HOME_VIEW, created = items.size))
    fun addMusicAlbum(name: String) = add(Item(UUID.randomUUID(), "MusicAlbum", name, MUSIC_VIEW, created = items.size))
    fun remove(id: UUID) { items.removeAll { it.id == id } }
    fun markPlayed(id: UUID) { items.first { it.id == id }.apply { played = true; userDataChanged = true } }

    private fun add(item: Item): UUID { items += item; return item.id }

    @Synchronized
    override fun dispatch(request: RecordedRequest): MockResponse {
        val url = request.url
        val path = url.encodedPath
        // The SDK repeats list parameters (includeItemTypes=Movie&includeItemTypes=Series); Jellyfin
        // also accepts commas, so accept both.
        fun param(name: String): String? = url.queryParameterNames.firstOrNull { it.equals(name, true) }
            ?.let { key -> url.queryParameterValues(key).filterNotNull().joinToString(",") }
        return when {
            path.endsWith("/UserViews") -> json(
                """{"Items":[${view(MOVIES_VIEW, "Movies", "movies")},${view(SHOWS_VIEW, "Shows", "tvshows")},${view(MUSIC_VIEW, "Music", "music")},${view(HOME_VIEW, "Home Videos", "homevideos")}],"TotalRecordCount":4,"StartIndex":0}"""
            )
            path.endsWith("/Ancestors") -> {
                val id = path.substringAfter("/Items/").substringBefore("/").replace("-", "")
                val item = items.first { it.id.toString().replace("-", "") == id }
                json("""[{"Id":"${item.view}","Type":"CollectionFolder","Name":"Library"}]""")
            }
            path.endsWith("/Items") -> {
                itemRequests++
                val types = param("includeItemTypes")?.split(',').orEmpty()
                val ids = param("ids")?.split(',')?.map { it.replace("-", "") }
                val parent = param("parentId")?.replace("-", "")
                val savedSince = param("minDateLastSaved") != null
                val userDataSince = param("minDateLastSavedForUser") != null
                if (savedSince || userDataSince) sawDateFilter = true
                var matches = items.filter { item ->
                    (ids == null || item.id.toString().replace("-", "") in ids) &&
                        (parent == null || item.view.toString().replace("-", "") == parent) &&
                        (types.isEmpty() || item.type in types)
                }
                // An incremental request only sees new items (saved since) or changed watch state.
                if (savedSince) matches = matches.filter { it.name == "New" }
                if (userDataSince) matches = matches.filter { it.userDataChanged }
                val start = param("startIndex")?.toInt() ?: 0
                val limit = param("limit")?.toInt() ?: matches.size
                val page = matches.sortedBy { it.created }.drop(start).take(limit)
                json("""{"Items":[${page.joinToString(",") { it.json() }}],"TotalRecordCount":${matches.size},"StartIndex":$start}""")
            }
            else -> MockResponse.Builder().code(404).build()
        }
    }

    private fun Item.json(): String = buildString {
        append("""{"Id":"$id","Type":"$type","Name":"$name","SortName":"${name.lowercase()}","ProductionYear":1999""")
        append(""","RunTimeTicks":72000000000,"ProviderIds":{"Tmdb":"603","Imdb":"tt0133093"}""")
        append(""","ImageTags":{"Primary":"tag-$id"},"BackdropImageTags":["backdrop"]""")
        if (seriesId != null) append(""","SeriesId":"$seriesId","SeriesName":"Show","ParentIndexNumber":$season,"IndexNumber":$episode""")
        append(""","UserData":{"PlaybackPositionTicks":0,"PlayCount":${if (played) 1 else 0},"IsFavorite":false,"Played":$played,"Key":"$id","ItemId":"$id"}}""")
    }

    private fun view(id: UUID, name: String, type: String) =
        """{"Id":"$id","Type":"CollectionFolder","Name":"$name","CollectionType":"$type"}"""

    private fun json(body: String) = MockResponse.Builder()
        .code(200)
        .setHeader("Content-Type", "application/json")
        .body(body)
        .build()

    companion object {
        val USER: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val MOVIES_VIEW: UUID = UUID.fromString("22222222-2222-2222-2222-222222222222")
        val SHOWS_VIEW: UUID = UUID.fromString("33333333-3333-3333-3333-333333333333")
        val MUSIC_VIEW: UUID = UUID.fromString("44444444-4444-4444-4444-444444444444")
        val HOME_VIEW: UUID = UUID.fromString("55555555-5555-5555-5555-555555555555")
    }
}
