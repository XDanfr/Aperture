package me.xdan.aperture.domain.hybrid

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import me.xdan.aperture.data.jellyfin.JellyfinAccounts
import me.xdan.aperture.data.local.dao.JellyfinDao
import me.xdan.aperture.domain.model.LibraryMode
import me.xdan.aperture.domain.repository.MediaRepository
import me.xdan.aperture.domain.repository.ProfileRepository

/** The active profile's [HybridIndex]: empty unless it shows both this device and a server. */
@OptIn(ExperimentalCoroutinesApi::class)
class HybridIndexSource(
    media: MediaRepository,
    accounts: JellyfinAccounts,
    profiles: ProfileRepository,
    dao: JellyfinDao
) {
    val index: Flow<HybridIndex> = combine(profiles.activeLibraryMode, accounts.activeAccount) { mode, account ->
        account.takeIf { mode == LibraryMode.HYBRID }
    }.flatMapLatest { account ->
        if (account == null) flowOf(HybridIndex(emptyList(), emptyList()))
        else combine(
            // A missing local copy shouldn't hide the server's.
            media.getAllMedia().map { all -> all.filter { it.missingSince == null } },
            dao.itemsOfType(account.id, "MOVIE"),
            dao.itemsOfType(account.id, "SERIES"),
            dao.itemsOfType(account.id, "EPISODE"),
            dao.libraries(account.id)
        ) { local, movies, series, episodes, libraries ->
            HybridMatcher.match(local, movies + series + episodes, libraries.associate { it.id to it.collectionType })
                .copy(baseUrl = account.baseUrl)
        }.flowOn(Dispatchers.Default)
    }
}
