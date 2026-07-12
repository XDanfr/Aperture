package me.xdan.aperture.data.remote.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface OpenSubtitlesApi {
    @GET("subtitles")
    suspend fun searchSubtitles(
        @Query("tmdb_id") tmdbId: Int? = null,
        @Query("query") query: String? = null,
        @Query("season_number") seasonNumber: Int? = null,
        @Query("episode_number") episodeNumber: Int? = null,
        @Query("languages") languages: String = "en",
        @Header("Api-Key") apiKey: String,
        @Header("User-Agent") userAgent: String = USER_AGENT
    ): OpenSubtitlesSearchResponse

    @POST("download")
    suspend fun createDownload(
        @Body request: OpenSubtitlesDownloadRequest,
        @Header("Api-Key") apiKey: String,
        @Header("User-Agent") userAgent: String = USER_AGENT
    ): OpenSubtitlesDownloadResponse

    companion object {
        const val BASE_URL = "https://api.opensubtitles.com/api/v1/"
        const val USER_AGENT = "Aperture v0.4"
    }
}

@JsonClass(generateAdapter = true)
data class OpenSubtitlesSearchResponse(
    @Json(name = "data") val data: List<OpenSubtitleResult> = emptyList()
)

@JsonClass(generateAdapter = true)
data class OpenSubtitleResult(
    @Json(name = "id") val id: String,
    @Json(name = "attributes") val attributes: OpenSubtitleAttributes
)

@JsonClass(generateAdapter = true)
data class OpenSubtitleAttributes(
    @Json(name = "language") val language: String? = null,
    @Json(name = "release") val release: String? = null,
    @Json(name = "hearing_impaired") val hearingImpaired: Boolean = false,
    @Json(name = "files") val files: List<OpenSubtitleFile> = emptyList()
)

@JsonClass(generateAdapter = true)
data class OpenSubtitleFile(
    @Json(name = "file_id") val fileId: Int,
    @Json(name = "file_name") val fileName: String? = null
)

@JsonClass(generateAdapter = true)
data class OpenSubtitlesDownloadRequest(
    @Json(name = "file_id") val fileId: Int
)

@JsonClass(generateAdapter = true)
data class OpenSubtitlesDownloadResponse(
    @Json(name = "link") val link: String,
    @Json(name = "file_name") val fileName: String? = null
)
