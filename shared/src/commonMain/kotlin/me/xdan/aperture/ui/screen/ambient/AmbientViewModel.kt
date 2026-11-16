package me.xdan.aperture.ui.screen.ambient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import me.xdan.aperture.data.jellyfin.isDiscover
import me.xdan.aperture.data.local.dao.JellyfinDao
import me.xdan.aperture.domain.catalog.JellyfinImages
import me.xdan.aperture.domain.model.AmbientSettings
import me.xdan.aperture.domain.repository.MediaRepository
import me.xdan.aperture.domain.repository.ProfileRepository
import me.xdan.aperture.domain.repository.UserPreferencesRepository

/**
 * What ambient mode shows: the active profile's titles, wherever they are. A local profile uses
 * this device's library, a Jellyfin one its server's films and shows (not Discover's requestable
 * placeholders), and a hybrid one both.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AmbientViewModel constructor(
    mediaRepository: MediaRepository,
    userPreferencesRepository: UserPreferencesRepository,
    private val artworkPalettes: me.xdan.aperture.data.artwork.ArtworkPaletteStore,
    profiles: ProfileRepository,
    catalog: me.xdan.aperture.data.jellyfin.JellyfinCatalog,
    dao: JellyfinDao
) : ViewModel() {

    private val local: Flow<List<AmbientArtwork>> = mediaRepository.getAllMedia()
        .map { media -> media.filterNot { it.isHidden }.map { it.toAmbientArtwork() } }

    private val server: Flow<List<AmbientArtwork>> = combine(catalog.activeAccount, catalog.libraries) { account, libraries -> account to libraries }
        .flatMapLatest { (account, libraries) ->
            if (account == null) return@flatMapLatest flowOf(emptyList())
            val discover = libraries.filter { it.isDiscover }.map { it.id }.toSet()
            val images = JellyfinImages(account.baseUrl)
            combine(dao.itemsOfType(account.id, "MOVIE"), dao.itemsOfType(account.id, "SERIES")) { movies, shows ->
                (movies + shows).filter { it.libraryId !in discover }.map { images.item(it).toAmbientArtwork() }
            }
        }

    val artwork: StateFlow<List<AmbientArtwork>> = profiles.activeLibraryMode.flatMapLatest { mode ->
        when {
            mode.usesLocal && mode.usesJellyfin -> combine(local, server) { a, b -> a + b }
            mode.usesJellyfin -> server
            else -> local
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<AmbientSettings> = combine(
        userPreferencesRepository.ambientMode,
        userPreferencesRepository.ambientWallBrandPlacement,
        userPreferencesRepository.ambientShowClock
    ) { mode, placement, showClock ->
        AmbientSettings(mode, placement, showClock)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AmbientSettings()
    )

    /** A TMDB path or a Jellyfin image URL; both are palette keys. */
    suspend fun artworkAccent(path: String): Int? = artworkPalettes.seed(path)
}
