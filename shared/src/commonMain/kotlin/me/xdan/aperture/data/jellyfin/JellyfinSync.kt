package me.xdan.aperture.data.jellyfin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.first
import me.xdan.aperture.data.local.dao.JellyfinDao
import me.xdan.aperture.data.local.entity.JellyfinAccountEntity
import me.xdan.aperture.data.local.entity.RemoteLibraryEntity
import me.xdan.aperture.platform.Log
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.userViewApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.LibraryUpdateInfo
import org.jellyfin.sdk.model.api.UserDataChangeInfo

/**
 * Keeps a profile's index of a server in step with it.
 *
 * - A full sync walks every indexable library and drops what it didn't see.
 * - An incremental sync asks for items saved, or user data changed, since the last sync. It can't
 *   see deletions; WebSocket messages and the periodic full sync cover those.
 * - [applyLibraryChange] and [applyUserDataChange] apply WebSocket messages directly.
 */
class JellyfinSync(
    private val dao: JellyfinDao,
    private val clients: JellyfinClients,
    private val clock: () -> Long = System::currentTimeMillis
) {
    data class Result(val items: Int, val removed: Int, val libraries: Int)

    class NotSignedIn : IllegalStateException("This profile's Jellyfin sign-in has expired")

    suspend fun fullSync(account: JellyfinAccountEntity): Result = withContext(Dispatchers.IO) {
        val api = clients.forAccount(account) ?: throw NotSignedIn()
        val startedAt = clock()
        val generation = startedAt
        val views = syncLibraries(api, account)
        // A few libraries at once: the server answers in parallel and Room serialises the writes.
        val count = java.util.concurrent.atomic.AtomicInteger()
        val permits = kotlinx.coroutines.sync.Semaphore(PARALLEL_LIBRARIES)
        coroutineScope {
            views.filter { it.isIndexed }.map { view ->
                async {
                    permits.withPermit {
                        forEachPage(api, account, parentId = view.id) { page ->
                            dao.upsertItems(page.mapNotNull { RemoteItemMapping.toEntity(it, account.id, view.id, generation) })
                            count.addAndGet(page.size)
                        }
                    }
                }
            }.awaitAll()
        }
        val removed = dao.deleteItemsNotIn(account.id, generation)
        dao.markFullySynced(account.id, startedAt - CLOCK_SKEW_MS)
        Log.i(TAG, "Full sync of ${account.serverName}: ${count.get()} items, $removed removed")
        Result(count.get(), removed, views.size)
    }

    /** Falls back to a full sync when there's never been one, or the last is over a day old. */
    suspend fun sync(account: JellyfinAccountEntity): Result {
        val lastFull = account.lastFullSyncAt
        val since = account.lastSyncAt
        if (lastFull == null || since == null || clock() - lastFull > FULL_SYNC_INTERVAL_MS) {
            return fullSync(account)
        }
        return incrementalSync(account, since)
    }

    suspend fun incrementalSync(account: JellyfinAccountEntity, since: Long): Result = withContext(Dispatchers.IO) {
        val api = clients.forAccount(account) ?: throw NotSignedIn()
        val startedAt = clock()
        val generation = startedAt
        val views = syncLibraries(api, account)
        val sinceUtc = RemoteItemMapping.epochMsToUtc(since)
        var count = 0
        views.filter { it.isIndexed }.forEach { view ->
            // Metadata changes and this user's watch state are separate filters on the server.
            forEachPage(api, account, parentId = view.id, savedSince = sinceUtc) { page ->
                dao.upsertItems(page.mapNotNull { RemoteItemMapping.toEntity(it, account.id, view.id, generation) })
                count += page.size
            }
            forEachPage(api, account, parentId = view.id, userDataSince = sinceUtc) { page ->
                dao.upsertItems(page.mapNotNull { RemoteItemMapping.toEntity(it, account.id, view.id, generation) })
                count += page.size
            }
        }
        dao.markSynced(account.id, startedAt - CLOCK_SKEW_MS)
        Result(count, 0, views.size)
    }

    /** A LibraryChanged WebSocket message: drop removed items, refetch added and updated ones. */
    suspend fun applyLibraryChange(account: JellyfinAccountEntity, change: LibraryUpdateInfo) = withContext(Dispatchers.IO) {
        if (change.itemsRemoved.isNotEmpty()) dao.deleteItems(account.id, change.itemsRemoved.map(::normalise))
        refetch(account, (change.itemsAdded + change.itemsUpdated).map(::normalise).distinct())
    }

    /** A UserDataChanged WebSocket message for this account's user. */
    suspend fun applyUserDataChange(account: JellyfinAccountEntity, change: UserDataChangeInfo) = withContext(Dispatchers.IO) {
        if (JellyfinIds.of(change.userId) != account.userId) return@withContext
        change.userDataList.forEach { data ->
            dao.updateUserData(
                accountId = account.id,
                itemId = JellyfinIds.of(data.itemId),
                played = data.played,
                positionMs = data.playbackPositionTicks / RemoteItemMapping.TICKS_PER_MS,
                playCount = data.playCount,
                isFavorite = data.isFavorite,
                lastPlayedAt = data.lastPlayedDate?.let { with(RemoteItemMapping) { it.toEpochMs() } }
            )
        }
    }

    private suspend fun refetch(account: JellyfinAccountEntity, itemIds: List<String>) {
        if (itemIds.isEmpty()) return
        val api = clients.forAccount(account) ?: throw NotSignedIn()
        val generation = clock()
        val libraryIds = libraryIdsOf(account)
        itemIds.chunked(PAGE_SIZE).forEach { chunk ->
            val items = api.libraryApi.getItems(
                userId = JellyfinIds.toUuid(account.userId),
                ids = chunk.map(JellyfinIds::toUuid),
                fields = RemoteItemMapping.fields,
                enableUserData = true,
                imageTypeLimit = 1,
                enableImageTypes = RemoteItemMapping.imageTypes,
            ).content.items
            // Keep each item's library from the index; new episodes take their series' library, and
            // anything else new looks for its library among its ancestors.
            val rows = items.filter { RemoteItemMapping.typeOf(it.type) != null }.map { item ->
                val id = JellyfinIds.of(item.id)
                val libraryId = dao.item(account.id, id)?.libraryId
                    ?: item.seriesId?.let { dao.item(account.id, JellyfinIds.of(it))?.libraryId }
                    ?: libraryFromAncestors(api, account, item.id, libraryIds)
                RemoteItemMapping.toEntity(item, account.id, libraryId, generation)!!
            }
            dao.upsertItems(rows)
        }
    }

    private suspend fun libraryIdsOf(account: JellyfinAccountEntity): Set<String> =
        dao.libraries(account.id).first().mapTo(mutableSetOf()) { it.id }

    private suspend fun libraryFromAncestors(
        api: ApiClient,
        account: JellyfinAccountEntity,
        itemId: java.util.UUID,
        libraryIds: Set<String>
    ): String? = runCatching {
        api.libraryApi.getAncestors(itemId = itemId, userId = JellyfinIds.toUuid(account.userId)).content
            .map { JellyfinIds.of(it.id) }
            .firstOrNull { it in libraryIds }
    }.getOrNull()

    private suspend fun syncLibraries(api: ApiClient, account: JellyfinAccountEntity): List<RemoteLibraryEntity> {
        val views = api.userViewApi.getUserViews(userId = JellyfinIds.toUuid(account.userId)).content.items
        val libraries = views.mapIndexed { index, view ->
            RemoteLibraryEntity(
                accountId = account.id,
                id = JellyfinIds.of(view.id),
                name = view.name ?: "",
                collectionType = view.collectionType?.serialName,
                sortIndex = index,
                primaryImageTag = view.imageTags?.get(org.jellyfin.sdk.model.api.ImageType.PRIMARY)
            )
        }
        dao.replaceLibraries(account.id, libraries)
        return libraries
    }

    private suspend fun forEachPage(
        api: ApiClient,
        account: JellyfinAccountEntity,
        parentId: String,
        savedSince: java.time.LocalDateTime? = null,
        userDataSince: java.time.LocalDateTime? = null,
        onPage: suspend (List<BaseItemDto>) -> Unit
    ) {
        var start = 0
        while (true) {
            val result = api.libraryApi.getItems(
                userId = JellyfinIds.toUuid(account.userId),
                parentId = JellyfinIds.toUuid(parentId),
                recursive = true,
                includeItemTypes = RemoteItemMapping.indexedKinds,
                fields = RemoteItemMapping.fields,
                enableUserData = true,
                imageTypeLimit = 1,
                enableImageTypes = RemoteItemMapping.imageTypes,
                // A stable order, so pages don't skip or repeat items.
                sortBy = listOf(ItemSortBy.DATE_CREATED, ItemSortBy.SORT_NAME),
                minDateLastSaved = savedSince,
                minDateLastSavedForUser = userDataSince,
                startIndex = start,
                limit = PAGE_SIZE,
                enableTotalRecordCount = true,
            ).content
            if (result.items.isNotEmpty()) onPage(result.items)
            start += result.items.size
            if (result.items.size < PAGE_SIZE || start >= result.totalRecordCount) break
        }
    }

    private fun normalise(id: String) = id.replace("-", "").lowercase()

    private val RemoteLibraryEntity.isIndexed: Boolean
        get() = collectionType in INDEXED_COLLECTIONS

    companion object {
        private const val TAG = "JellyfinSync"
        const val PAGE_SIZE = 500
        const val PARALLEL_LIBRARIES = 3
        /** Covers a server clock a little behind this device's. */
        const val CLOCK_SKEW_MS = 5 * 60 * 1000L
        const val FULL_SYNC_INTERVAL_MS = 24 * 60 * 60 * 1000L

        /**
         * Movies, shows, home videos and mixed folders. Every library is still listed (for the
         * sidebar); music, books, photos, playlists and Live TV just aren't indexed.
         */
        val INDEXED_COLLECTIONS: Set<String?> = setOf(
            CollectionType.MOVIES.serialName,
            CollectionType.TVSHOWS.serialName,
            CollectionType.HOMEVIDEOS.serialName,
            null
        )
    }
}
