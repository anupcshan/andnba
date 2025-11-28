package `in`.anupcshan.gswtracker.data.api

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Tracks network data usage for this app with persistence
 */
object DataUsageTracker {
    private const val PREFS_NAME = "data_usage_prefs"
    private const val KEY_BYTES_USED = "bytes_used"

    private var prefs: android.content.SharedPreferences? = null
    private val _bytesUsed = MutableStateFlow(0L)
    val bytesUsed: StateFlow<Long> = _bytesUsed.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _bytesUsed.value = prefs?.getLong(KEY_BYTES_USED, 0L) ?: 0L
    }

    fun addBytes(bytes: Long) {
        _bytesUsed.value += bytes
        prefs?.edit()?.putLong(KEY_BYTES_USED, _bytesUsed.value)?.apply()
    }

    fun reset() {
        _bytesUsed.value = 0L
        prefs?.edit()?.putLong(KEY_BYTES_USED, 0L)?.apply()
    }
}

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
        val request = chain.request()
        val response = chain.proceed(request)

        // Apply different cache TTL based on endpoint
        // Box score: 24 hours (arena name never changes for a game)
        // Play-by-play: 5 minutes (quarter data doesn't change, allows app reopen)
        // Scoreboard: 60 seconds (needs to be fresh for live updates)
        val url = request.url.toString()
        val maxAge = when {
            url.contains("boxscore") -> 86400 // 24 hours
            url.contains("playbyplay") -> 300 // 5 minutes
            else -> 60 // 1 minute (scoreboard, schedule)
        }

        response.newBuilder()
            .header("Cache-Control", "public, max-age=$maxAge")
            .removeHeader("Pragma")
            .build()
    }

    private val dataUsageInterceptor = okhttp3.Interceptor { chain ->
        val response = chain.proceed(chain.request())
        // Track actual bytes received on the wire (compressed size)
        // Use Content-Length header from network response (before decompression)
        val bytesReceived = response.header("Content-Length")?.toLongOrNull() ?: 0
        if (bytesReceived > 0) {
            DataUsageTracker.addBytes(bytesReceived)
        }
        response
    }

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .cache(cache)
        .addNetworkInterceptor(cacheInterceptor)
        .addNetworkInterceptor(dataUsageInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        const val BASE_URL = "https://cdn.nba.com/static/json/liveData"
        const val SCOREBOARD_URL = "$BASE_URL/scoreboard/todaysScoreboard_00.json"
        const val SCHEDULE_URL = "https://cdn.nba.com/static/json/staticData/scheduleLeagueV2.json"

        fun getPlayByPlayUrl(gameId: String): String {
            return "$BASE_URL/playbyplay/playbyplay_$gameId.json"
        }

        fun getBoxScoreUrl(gameId: String): String {
            return "$BASE_URL/boxscore/boxscore_$gameId.json"
        }

        /**
         * Get standings URL for the current NBA season
         * NBA season runs October to June, so:
         * - Oct-Dec: current year to next year (e.g., Oct 2025 = 2025-26)
         * - Jan-Jun: previous year to current year (e.g., Jan 2026 = 2025-26)
         * - Jul-Sep: off-season, use previous season (e.g., Jul 2025 = 2024-25)
         */
        fun getStandingsUrl(): String {
            val now = LocalDate.now()
            val currentYear = now.year
            val currentMonth = now.monthValue

            val season = when {
                currentMonth >= 10 -> "$currentYear-${(currentYear + 1) % 100}"
                currentMonth <= 6 -> "${currentYear - 1}-${currentYear % 100}"
                else -> "${currentYear - 1}-${currentYear % 100}" // Jul-Sep: off-season
            }

            return "https://stats.nba.com/stats/leaguestandingsv3?LeagueID=00&Season=$season&SeasonType=Regular+Season"
        }
    }
}
