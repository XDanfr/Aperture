package me.xdan.aperture.data.jellyfin

import androidx.room.RoomRawQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import me.xdan.aperture.data.local.dao.JellyfinDao
import me.xdan.aperture.data.local.entity.JellyfinAccountEntity
import me.xdan.aperture.data.local.entity.RemoteLibraryEntity
import me.xdan.aperture.domain.catalog.CatalogItem
import me.xdan.aperture.domain.catalog.JellyfinImages
import me.xdan.aperture.domain.catalog.LibraryQuery
import me.xdan.aperture.platform.KeyValueStores
import me.xdan.aperture.domain.catalog.MediaInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.showApi
import org.jellyfin.sdk.api.client.extensions.userDataApi
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.VideoRangeType
import org.jellyfin.sdk.model.serializer.toUUIDOrNull

/** Reads the active profile's Jellyfin index for the screens. */
class JellyfinCatalog(
    private val dao: JellyfinDao,
    private val accounts: JellyfinAccounts,
    stores: KeyValueStores
) {
    private val queries = stores.open(QUERY_STORE)

    val activeAccount: Flow<JellyfinAccountEntity?> = accounts.activeAccount

    /** Every library the user can see, in the server's order. */
    val libraries: Flow<List<RemoteLibraryEntity>> = activeAccount.flatMapLatest { account ->
        account?.let { dao.libraries(it.id) } ?: flowOf(emptyList())
    }

    fun browse(account: JellyfinAccountEntity, libraryId: String, query: LibraryQuery, seed: Int): Flow<List<CatalogItem>> {
        val images = JellyfinImages(account.baseUrl)
        return dao.itemsRaw(sql(account.id, libraryId, query, seed)).map { rows -> rows.map(images::item) }
    }

    fun continueWatching(account: JellyfinAccountEntity, limit: Int = ROW_LIMIT): Flow<List<CatalogItem>> {
        val images = JellyfinImages(account.baseUrl)
        return dao.continueWatching(account.id, limit).map { rows -> rows.map(images::item) }
    }

    fun recentlyAdded(account: JellyfinAccountEntity, libraryId: String, limit: Int = ROW_LIMIT): Flow<List<CatalogItem>> {
        val images = JellyfinImages(account.baseUrl)
        return dao.recentlyAdded(account.id, libraryId, limit).map { rows -> rows.map(images::item) }
    }

    suspend fun spotlight(account: JellyfinAccountEntity, count: Int = 5): List<CatalogItem> {
        val images = JellyfinImages(account.baseUrl)
        return dao.spotlightCandidates(account.id, count).map(images::item)
    }

    fun favorites(account: JellyfinAccountEntity): Flow<List<CatalogItem>> {
        val images = JellyfinImages(account.baseUrl)
        return dao.favorites(account.id).map { rows -> rows.map(images::item) }
    }

    fun search(account: JellyfinAccountEntity, query: String, limit: Int = 200): Flow<List<CatalogItem>> {
        val images = JellyfinImages(account.baseUrl)
        val escaped = query.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return dao.search(account.id, escaped, limit).map { rows -> rows.map(images::item) }
    }

    fun item(account: JellyfinAccountEntity, itemId: String): Flow<CatalogItem?> {
        val images = JellyfinImages(account.baseUrl)
        return dao.observeItem(account.id, itemId).map { it?.let(images::item) }
    }

    fun episodes(account: JellyfinAccountEntity, seriesId: String): Flow<List<CatalogItem>> {
        val images = JellyfinImages(account.baseUrl)
        return dao.observeEpisodes(account.id, seriesId).map { rows -> rows.map(images::item) }
    }

    /**
     * Adds or removes a server favourite (My List for Jellyfin titles). The index updates first so
     * the button responds at once, and goes back if the server says no.
     */
    suspend fun setFavorite(account: JellyfinAccountEntity, itemId: String, favorite: Boolean): Boolean = withContext(Dispatchers.IO) {
        dao.setFavorite(account.id, itemId, favorite)
        val ok = runCatching {
            val api = accounts.api(account) ?: error("Not signed in")
            val id = itemId.toUUIDOrNull() ?: error("Bad id")
            if (favorite) api.userDataApi.markFavoriteItem(id) else api.userDataApi.unmarkFavoriteItem(id)
        }.isSuccess
        if (!ok) dao.setFavorite(account.id, itemId, !favorite)
        ok
    }

    /** Marks a title watched or unwatched on the server, the same way as [setFavorite]. */
    suspend fun setPlayed(account: JellyfinAccountEntity, itemId: String, played: Boolean): Boolean = withContext(Dispatchers.IO) {
        val before = dao.observeItem(account.id, itemId).first()
        dao.setPlayed(account.id, itemId, played)
        val ok = runCatching {
            val api = accounts.api(account) ?: error("Not signed in")
            val id = itemId.toUUIDOrNull() ?: error("Bad id")
            if (played) api.userDataApi.markPlayedItem(id) else api.userDataApi.markUnplayedItem(id)
        }.isSuccess
        if (!ok && before != null) dao.upsertItems(listOf(before))
        ok
    }

    /** The Info dialog for a server item: where it is, the file, and its tracks. Null when unreachable. */
    suspend fun mediaInfo(account: JellyfinAccountEntity, itemId: String, libraryName: String?): MediaInfo? = withContext(Dispatchers.IO) {
        val api = accounts.api(account) ?: return@withContext null
        val id = itemId.toUUIDOrNull() ?: return@withContext null
        val item = runCatching { api.libraryApi.getItem(id).content }.getOrNull() ?: return@withContext null
        val source = item.mediaSources?.firstOrNull()
        val sections = mutableListOf<MediaInfo.Section>()
        sections += MediaInfo.Section("Location", buildList {
            add(MediaInfo.Row("Server", account.serverName.ifBlank { account.baseUrl }))
            libraryName?.let { add(MediaInfo.Row("Library", it)) }
            (source?.path ?: item.path)?.let { add(MediaInfo.Row("Path", it)) }
        })
        if (source != null) {
            sections += MediaInfo.Section("File", buildList {
                source.container?.let { add(MediaInfo.Row("Container", it)) }
                source.size?.takeIf { it > 0 }?.let { add(MediaInfo.Row("Size", MediaInfo.formatSize(it))) }
                source.runTimeTicks?.takeIf { it > 0 }?.let { add(MediaInfo.Row("Duration", MediaInfo.formatDuration(it / 10_000))) }
                source.bitrate?.takeIf { it > 0 }?.let { add(MediaInfo.Row("Bitrate", MediaInfo.formatBitrate(it.toLong()))) }
                item.dateCreated?.let { add(MediaInfo.Row("Added", it.toLocalDate().toString())) }
                if ((item.mediaSources?.size ?: 0) > 1) add(MediaInfo.Row("Versions", "${item.mediaSources!!.size}"))
            })
            val streams = source.mediaStreams.orEmpty()
            fun section(title: String, type: MediaStreamType, line: (org.jellyfin.sdk.model.api.MediaStream) -> String) {
                val rows = streams.filter { it.type == type }.map { MediaInfo.Row(null, line(it)) }
                if (rows.isNotEmpty()) sections += MediaInfo.Section(title, rows)
            }
            section("Video", MediaStreamType.VIDEO) { s ->
                listOfNotNull(
                    s.width?.let { w -> s.height?.let { h -> MediaInfo.resolutionLabel(w, h) } },
                    s.codec?.uppercase(),
                    s.profile,
                    s.videoRangeType.takeIf { it != VideoRangeType.UNKNOWN && it != VideoRangeType.SDR }?.serialName,
                    s.bitDepth?.let { "$it-bit" },
                    s.realFrameRate?.let { "${"%.3f".format(it).trimEnd('0').trimEnd('.')} fps" }
                ).joinToString(" · ")
            }
            section("Audio", MediaStreamType.AUDIO) { s -> s.displayTitle ?: listOfNotNull(s.language, s.codec, s.channels?.let(MediaInfo::channelLabel)).joinToString(" · ") }
            section("Subtitles", MediaStreamType.SUBTITLE) { s ->
                (s.displayTitle ?: listOfNotNull(s.language, s.codec).joinToString(" · ")) + if (s.isExternal) " (external)" else ""
            }
        }
        MediaInfo(sections)
    }

    /** The server's Next Up: the next episode of each show in progress. Empty when unreachable. */
    suspend fun nextUp(account: JellyfinAccountEntity, limit: Int = ROW_LIMIT): List<CatalogItem> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        // SDK calls stay off the main thread: on cancellation the SDK closes the response where it resumes.
        val api = accounts.api(account) ?: return@withContext emptyList()
        val images = JellyfinImages(account.baseUrl)
        runCatching {
            api.showApi.getNextUp(
                userId = JellyfinIds.toUuid(account.userId),
                limit = limit,
                fields = RemoteItemMapping.fields,
                enableUserData = true,
                imageTypeLimit = 1,
                enableImageTypes = RemoteItemMapping.imageTypes
            ).content.items.mapNotNull { dto ->
                RemoteItemMapping.toEntity(dto, account.id, null, 0)?.let(images::item)
            }
        }.getOrDefault(emptyList())
    }

    fun savedQuery(profileId: Long, libraryId: String): LibraryQuery =
        LibraryQuery.decode(queries.getString("$profileId:$libraryId", null))

    fun saveQuery(profileId: Long, libraryId: String, query: LibraryQuery) {
        queries.edit().putString("$profileId:$libraryId", query.encode()).apply()
    }

    private fun sql(accountId: Long, libraryId: String, query: LibraryQuery, seed: Int): RoomRawQuery {
        val where = buildList {
            add("accountId = ?")
            add("libraryId = ?")
            add("type IN ('MOVIE', 'SERIES', 'VIDEO')")
            if (query.unwatchedOnly) add("played = 0")
            if (query.favouritesOnly) add("isFavorite = 1")
            if (query.genre != null) add("instr('|' || COALESCE(genres, '') || '|', ?) > 0")
        }.joinToString(" AND ")
        val direction = if (query.descending) "DESC" else "ASC"
        val title = "COALESCE(sortName, name) COLLATE NOCASE"
        val order = when (query.sort) {
            LibraryQuery.Sort.TITLE -> "$title $direction"
            LibraryQuery.Sort.DATE_ADDED -> "dateCreated IS NULL, dateCreated $direction, $title"
            LibraryQuery.Sort.RELEASED -> "COALESCE(premiereDate, year) IS NULL, premiereDate $direction, year $direction, $title"
            LibraryQuery.Sort.RATING -> "communityRating IS NULL, communityRating $direction, $title"
            LibraryQuery.Sort.RUNTIME -> "runtimeMs IS NULL, runtimeMs $direction, $title"
            LibraryQuery.Sort.LAST_PLAYED -> "lastPlayedAt IS NULL, lastPlayedAt $direction, $title"
            // Stable for a given seed, so the grid doesn't reshuffle when the index updates.
            LibraryQuery.Sort.RANDOM -> "(rowid * ?) % 1000003, $title"
        }
        val sql = "SELECT * FROM remote_items WHERE $where ORDER BY $order"
        return RoomRawQuery(sql) { statement ->
            var index = 1
            statement.bindLong(index++, accountId)
            statement.bindText(index++, libraryId)
            query.genre?.let { statement.bindText(index++, "|$it|") }
            if (query.sort == LibraryQuery.Sort.RANDOM) statement.bindLong(index, (seed.toLong() and 0xFFFF) + 7919)
        }
    }

    companion object {
        const val ROW_LIMIT = 20
        private const val QUERY_STORE = "library_queries"
    }
}
