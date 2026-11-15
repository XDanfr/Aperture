package me.xdan.aperture.data.remote.api

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class OpenSubtitlesApiTest {
    @Test
    fun loginRequestsJson() = runBlocking {
        val api = apiReturning("""{"token":"test-token","base_url":"api.opensubtitles.com"}""")

        val response = api.login(OpenSubtitlesLoginRequest("test", "test"), "test-key", "Aperture test")

        assertEquals("test-token", response.token)
    }

    @Test
    fun searchRequestsJson() = runBlocking {
        val api = apiReturning("""{"data":[{"id":"1","attributes":{"files":[{"file_id":42}]}}]}""")

        val response = api.searchSubtitles(
            url = "https://api.opensubtitles.com/api/v1/subtitles",
            query = "test",
            apiKey = "test-key",
            authorization = "Bearer test-token",
            userAgent = "Aperture test"
        )

        assertEquals(42, response.data.single().attributes.files.single().fileId)
    }

    @Test
    fun downloadRequestsJsonInsteadOfServiceUnavailableHtml() = runBlocking {
        val api = apiReturning("""{"link":"https://example.com/subtitle.srt","file_name":"subtitle.srt"}""")

        val response = api.createDownload(
            "https://api.opensubtitles.com/api/v1/download",
            OpenSubtitlesDownloadRequest(42),
            "test-key",
            "Bearer test-token",
            "Aperture test"
        )

        assertEquals("https://example.com/subtitle.srt", response.link)
    }

    private fun apiReturning(json: String): OpenSubtitlesApi {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            // Reproduce the provider's observed behavior without making network requests.
            val acceptsJson = chain.request().header("Accept") == "application/json"
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(if (acceptsJson) 200 else 503)
                .message(if (acceptsJson) "OK" else "Service Unavailable")
                .body(
                    if (acceptsJson) json.toResponseBody("application/json".toMediaType())
                    else "<html>Service Temporarily Unavailable</html>".toResponseBody("text/html".toMediaType())
                )
                .build()
        }.build()
        return Retrofit.Builder()
            .baseUrl(OpenSubtitlesApi.BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(OpenSubtitlesApi::class.java)
    }
}
