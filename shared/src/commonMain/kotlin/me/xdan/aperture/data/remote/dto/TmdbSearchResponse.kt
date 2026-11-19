package me.xdan.aperture.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TmdbSearchResponse(
    @Json(name = "results") val results: List<TmdbResult>
)

@JsonClass(generateAdapter = true)
data class TmdbResult(
    @Json(name = "id") val id: Int,
    @Json(name = "title") val title: String?,
    @Json(name = "name") val name: String?,
    @Json(name = "overview") val overview: String?,
    @Json(name = "poster_path") val posterPath: String?,
    @Json(name = "backdrop_path") val backdropPath: String?,
    @Json(name = "release_date") val releaseDate: String?,
    @Json(name = "first_air_date") val firstAirDate: String?,
    @Json(name = "vote_average") val voteAverage: Double?,
    @Json(name = "genre_ids") val genreIds: List<Int>? = null
) {
    /** Genre names, "|"-separated, as stored on a title. */
    val genreNames: String? get() = genreIds?.mapNotNull(TmdbGenres::name)?.distinct()?.takeIf { it.isNotEmpty() }?.joinToString("|")

    val rating: Float? get() = voteAverage?.toFloat()?.takeIf { it > 0f }
}

/** TMDB's movie and TV genre ids, which rarely change. */
object TmdbGenres {
    private val names = mapOf(
        28 to "Action", 12 to "Adventure", 16 to "Animation", 35 to "Comedy", 80 to "Crime",
        99 to "Documentary", 18 to "Drama", 10751 to "Family", 14 to "Fantasy", 36 to "History",
        27 to "Horror", 10402 to "Music", 9648 to "Mystery", 10749 to "Romance", 878 to "Science Fiction",
        10770 to "TV Movie", 53 to "Thriller", 10752 to "War", 37 to "Western",
        10759 to "Action & Adventure", 10762 to "Kids", 10763 to "News", 10764 to "Reality",
        10765 to "Sci-Fi & Fantasy", 10766 to "Soap", 10767 to "Talk", 10768 to "War & Politics"
    )

    fun name(id: Int): String? = names[id]
}

@JsonClass(generateAdapter = true)
data class TmdbEpisodeResult(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String?,
    @Json(name = "overview") val overview: String?,
    @Json(name = "still_path") val stillPath: String?,
    @Json(name = "air_date") val airDate: String?
)
