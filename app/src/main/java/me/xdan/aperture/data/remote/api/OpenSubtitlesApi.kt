package me.xdan.aperture.data.remote.api

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface OpenSubtitlesApi {
    @GET("subtitles")
    suspend fun searchSubtitles(
        @Query("tmdb_id") tmdbId: Int?,
        @Query("query") query: String?,
        @Header("Api-Key") apiKey: String
    ): Any // Placeholder for now

    companion object {
        const val BASE_URL = "https://api.opensubtitles.com/api/v1/"
    }
}
