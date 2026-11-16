package me.xdan.aperture.domain.sidebar

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import me.xdan.aperture.domain.repository.ProfileRepository
import me.xdan.aperture.platform.KeyValueStores

/**
 * One profile's sidebar between Home (always first) and Settings (always last): the order of
 * entries it has arranged and the ones it hid. Entries are keyed: "search", "movies", "shows",
 * "my_list", and "library:<id>" for each server library.
 */
data class SidebarLayout(val order: List<String> = emptyList(), val hidden: Set<String> = emptySet()) {

    /**
     * The entries to show, in order. [available] is what exists right now in its default order;
     * entries the profile hasn't placed (a library added on the server since) go after the entry
     * that precedes them by default, so they turn up somewhere sensible.
     */
    fun arrange(available: List<String>): List<String> = arrangeAll(available).filterNot { it in hidden }

    /** Like [arrange], hidden entries included, for the editor. */
    fun arrangeAll(available: List<String>): List<String> {
        val result = order.filter { it in available }.toMutableList()
        available.forEachIndexed { index, key ->
            if (key in result) return@forEachIndexed
            val predecessor = available.subList(0, index).lastOrNull { it in result }
            val at = predecessor?.let { result.indexOf(it) + 1 } ?: 0
            result.add(at, key)
        }
        return result
    }

    fun encode(): String = order.joinToString(",") + "|" + hidden.joinToString(",")

    companion object {
        fun decode(value: String?): SidebarLayout {
            if (value.isNullOrEmpty()) return SidebarLayout()
            val (order, hidden) = value.split('|', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            return SidebarLayout(
                order.split(',').filter { it.isNotBlank() },
                hidden.split(',').filter { it.isNotBlank() }.toSet()
            )
        }
    }
}

/** Each profile's [SidebarLayout], saved on this device. */
@OptIn(ExperimentalCoroutinesApi::class)
class SidebarRepository(stores: KeyValueStores, private val profiles: ProfileRepository) {
    private val store = stores.open("sidebar_layout")
    /** Bumped on save, so [layout] re-reads. */
    private val version = MutableStateFlow(0)

    val layout: Flow<SidebarLayout> = combine(profiles.activeProfileId, version) { id, _ ->
        SidebarLayout.decode(store.getString(key(id), null))
    }

    suspend fun save(layout: SidebarLayout) {
        store.edit().putString(key(profiles.activeProfileId.first()), layout.encode()).apply()
        version.value++
    }

    suspend fun reset() {
        store.edit().remove(key(profiles.activeProfileId.first())).apply()
        version.value++
    }

    private fun key(profileId: Long) = "p$profileId"
}
