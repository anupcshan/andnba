package `in`.anupcshan.gswtracker.data.api

import android.content.Context
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * NBA API client with caching support
 */
class NbaApiClient(context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private val cache = Cache(
        directory = File(context.cacheDir, "http_cache"),
        maxSize = 10L * 1024 * 1024 // 10 MB
    )

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val cacheInterceptor = okhttp3.Interceptor { chain ->
        val response = chain.proceed(chain.request())
        // Force cache with 60s TTL since NBA CDN doesn't send Cache-Control headers
        response.newBuilder()
            .header("Cache-Control", "public, max-age=60")
            .removeHeader("Pragma")
            .build()
    }

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .cache(cache)
        .addNetworkInterceptor(cacheInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        const val BASE_URL = "https://cdn.nba.com/static/json/liveData"
        const val SCOREBOARD_URL = "$BASE_URL/scoreboard/todaysScoreboard_00.json"

        fun getPlayByPlayUrl(gameId: String): String {
            return "$BASE_URL/playbyplay/playbyplay_$gameId.json"
        }

        fun getBoxScoreUrl(gameId: String): String {
            return "$BASE_URL/boxscore/boxscore_$gameId.json"
        }
    }
}
