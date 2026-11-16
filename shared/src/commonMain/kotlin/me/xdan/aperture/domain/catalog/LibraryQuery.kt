package me.xdan.aperture.domain.catalog

/** How a library grid is ordered and narrowed. Each library remembers its own. */
data class LibraryQuery(
    val sort: Sort = Sort.TITLE,
    val descending: Boolean = false,
    val unwatchedOnly: Boolean = false,
    val favouritesOnly: Boolean = false,
    val genre: String? = null
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

    val isFiltered: Boolean get() = unwatchedOnly || favouritesOnly || genre != null

    fun encode(): String = listOf(sort.name, descending, unwatchedOnly, favouritesOnly, genre.orEmpty()).joinToString("\u001F")

    companion object {
        fun decode(value: String?): LibraryQuery {
            val parts = value?.split('\u001F') ?: return LibraryQuery()
            return runCatching {
                LibraryQuery(
                    sort = Sort.valueOf(parts[0]),
                    descending = parts[1].toBoolean(),
                    unwatchedOnly = parts[2].toBoolean(),
                    favouritesOnly = parts[3].toBoolean(),
                    genre = parts.getOrNull(4)?.takeIf { it.isNotBlank() }
                )
            }.getOrDefault(LibraryQuery())
        }
    }
}
