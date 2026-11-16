package me.xdan.aperture.domain.catalog

/** How a library grid is ordered and narrowed. Each library remembers its own. */
data class LibraryQuery(
    val sort: Sort = Sort.TITLE,
    val descending: Boolean = false,
    val unwatchedOnly: Boolean = false,
    val favouritesOnly: Boolean = false,
    val genre: String? = null,
    /** The decade's first year, e.g. 1990 for the 1990s. */
    val decade: Int? = null,
    val uhdOnly: Boolean = false
) {
    enum class Sort(val label: String, val defaultDescending: Boolean) {
        TITLE("Title", false),
        DATE_ADDED("Date added", true),
        RELEASED("Release date", true),
        RATING("Rating", true),
        RUNTIME("Runtime", false),
        LAST_PLAYED("Last watched", true),
        RANDOM("Random", false)
    }

    val isFiltered: Boolean get() = unwatchedOnly || favouritesOnly || genre != null || decade != null || uhdOnly

    fun encode(): String = listOf(
        sort.name, descending, unwatchedOnly, favouritesOnly, genre.orEmpty(), decade?.toString().orEmpty(), uhdOnly
    ).joinToString("\u001F")

    companion object {
        /** Reads a saved query; fields added later fall back to their defaults. */
        fun decode(value: String?): LibraryQuery {
            val parts = value?.split('\u001F') ?: return LibraryQuery()
            return runCatching {
                LibraryQuery(
                    sort = Sort.valueOf(parts[0]),
                    descending = parts[1].toBoolean(),
                    unwatchedOnly = parts[2].toBoolean(),
                    favouritesOnly = parts[3].toBoolean(),
                    genre = parts.getOrNull(4)?.takeIf { it.isNotBlank() },
                    decade = parts.getOrNull(5)?.toIntOrNull(),
                    uhdOnly = parts.getOrNull(6)?.toBoolean() ?: false
                )
            }.getOrDefault(LibraryQuery())
        }

        fun decadeLabel(decade: Int): String = "${decade}s"
    }
}

/** Remembers each library's sort and filters, per profile. Local grids use ids like "local:movies". */
class LibraryQueryStore(stores: me.xdan.aperture.platform.KeyValueStores) {
    private val queries = stores.open("library_queries")

    fun load(profileId: Long, libraryId: String): LibraryQuery =
        LibraryQuery.decode(queries.getString("$profileId:$libraryId", null))

    fun save(profileId: Long, libraryId: String, query: LibraryQuery) {
        queries.edit().putString("$profileId:$libraryId", query.encode()).apply()
    }
}

/** The letter a title files under, ignoring a leading "The"/"A"/"An" like Jellyfin's sort names. "#" covers the rest. */
fun sortLetter(title: String): Char {
    val trimmed = title.trim().replace(Regex("^(the|a|an)\\s+", RegexOption.IGNORE_CASE), "")
    val first = trimmed.firstOrNull()?.uppercaseChar() ?: return '#'
    return if (first in 'A'..'Z') first else '#'
}

/** The first position of each letter in a title-sorted list, for the A–Z rail. */
fun letterIndex(titles: List<String>, descending: Boolean): Map<Char, Int> {
    val index = LinkedHashMap<Char, Int>()
    titles.forEachIndexed { position, title ->
        val letter = sortLetter(title)
        if (letter !in index) index[letter] = position
    }
    return if (descending) index else index.toSortedMap(compareBy { if (it == '#') ' ' else it })
}

/** Title order that files "The Matrix" under M, matching [sortLetter]. */
fun sortableTitle(title: String): String =
    title.trim().replace(Regex("^(the|a|an)\\s+", RegexOption.IGNORE_CASE), "").lowercase()
