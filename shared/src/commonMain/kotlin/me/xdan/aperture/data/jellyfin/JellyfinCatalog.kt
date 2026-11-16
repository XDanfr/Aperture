package me.xdan.aperture.data.jellyfin

import androidx.room.RoomRawQuery
import kotlinx.coroutines.flow.Flow
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
import org.jellyfin.sdk.api.client.extensions.showApi

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
