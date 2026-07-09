package me.xdan.aperture.data.remote.api

import me.xdan.aperture.data.remote.dto.TmdbSearchResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface TmdbApi {
    @GET("search/movie")
    suspend fun searchMovie(
        @Query("query") query: String,
        @Query("year") year: Int? = null,
        @Query("api_key") apiKey: String
    ): TmdbSearchResponse

    @GET("search/tv")
    suspend fun searchTvShow(
        @Query("query") query: String,
        @Query("first_air_date_year") year: Int? = null,
        @Query("api_key") apiKey: String
    ): TmdbSearchResponse
    
    companion object {
        const val BASE_URL = "https://api.themoviedb.org/3/"
        const val IMAGE_BASE_URL = "https://image.tmdb.org/t/p/"
    }
}
