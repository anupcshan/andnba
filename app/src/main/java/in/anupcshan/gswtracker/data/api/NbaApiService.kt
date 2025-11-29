package `in`.anupcshan.gswtracker.data.api

import android.util.Log
import `in`.anupcshan.gswtracker.data.model.BoxScoreResponse
import `in`.anupcshan.gswtracker.data.model.PlayByPlayResponse
import `in`.anupcshan.gswtracker.data.model.ScoreboardResponse
import `in`.anupcshan.gswtracker.data.model.ScheduleResponse
import `in`.anupcshan.gswtracker.data.model.StandingsResponse
import `in`.anupcshan.gswtracker.data.model.TeamRecord
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Service for making NBA API calls
 */
class NbaApiService(
    private val httpClient: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    companion object {
        private const val TAG = "NbaApiService"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    /**
     * Fetch today's scoreboard
     */
    suspend fun getScoreboard(): Result<ScoreboardResponse> = withContext(ioDispatcher) {
        try {
            val request = Request.Builder()
                .url(NbaApiClient.SCOREBOARD_URL)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("HTTP ${response.code}: ${response.message}")
                    )
                }

                val body = response.body?.string()
                    ?: return@withContext Result.failure(Exception("Empty response body"))

                val scoreboard = json.decodeFromString<ScoreboardResponse>(body)
                Result.success(scoreboard)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch play-by-play data for a specific game
     * @param forceRefresh If true, tries network first but falls back to cache on failure
     */
    suspend fun getPlayByPlay(gameId: String, forceRefresh: Boolean = false): Result<PlayByPlayResponse> = withContext(ioDispatcher) {
        try {
            val requestBuilder = Request.Builder()
                .url(NbaApiClient.getPlayByPlayUrl(gameId))

            if (forceRefresh) {
                requestBuilder.cacheControl(CacheControl.FORCE_NETWORK)
            }

            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("HTTP ${response.code}: ${response.message}")
                    )
                }

                val body = response.body?.string()
                    ?: return@withContext Result.failure(Exception("Empty response body"))

                val playByPlay = json.decodeFromString<PlayByPlayResponse>(body)
                Result.success(playByPlay)
            }
        } catch (e: Exception) {
            // If force refresh failed (e.g., no network), try cache as fallback
            if (forceRefresh) {
                Log.w(TAG, "getPlayByPlay: Network failed, trying cache fallback", e)
                return@withContext getPlayByPlay(gameId, forceRefresh = false)
            }
            Result.failure(e)
        }
    }

    /**
     * Try to get play-by-play data from cache only (no network call)
     * Accepts stale cache to restore last known worm state across app restarts
     * Caller is responsible for checking if period changed and refetching if needed
     * Returns null if cache miss
     */
    suspend fun getPlayByPlayFromCache(gameId: String): Result<PlayByPlayResponse?> = withContext(ioDispatcher) {
        try {
            val request = Request.Builder()
                .url(NbaApiClient.getPlayByPlayUrl(gameId))
                .cacheControl(
                    CacheControl.Builder()
                        .onlyIfCached()
                        .maxStale(Integer.MAX_VALUE, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                )
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    // Cache miss or would require network
                    return@withContext Result.success(null)
                }

                val body = response.body?.string()
                    ?: return@withContext Result.success(null)

                val playByPlay = json.decodeFromString<PlayByPlayResponse>(body)
                Result.success(playByPlay)
            }
        } catch (e: Exception) {
            // Cache miss or error
            Result.success(null)
        }
    }

    /**
     * Fetch boxscore data for a specific game (includes arena info)
     */
    suspend fun getBoxScore(gameId: String): Result<BoxScoreResponse> = withContext(ioDispatcher) {
        try {
            val request = Request.Builder()
                .url(NbaApiClient.getBoxScoreUrl(gameId))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("HTTP ${response.code}: ${response.message}")
                    )
                }

                val body = response.body?.string()
                    ?: return@withContext Result.failure(Exception("Empty response body"))

                val boxScore = json.decodeFromString<BoxScoreResponse>(body)
                Result.success(boxScore)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch full season schedule
     */
    suspend fun getSchedule(): Result<ScheduleResponse> = withContext(ioDispatcher) {
        try {
            val request = Request.Builder()
                .url(NbaApiClient.SCHEDULE_URL)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val error = "HTTP ${response.code}: ${response.message}"
                    Log.e(TAG, "getSchedule: $error")
                    return@withContext Result.failure(Exception(error))
                }

                val body = response.body?.string()
                if (body == null) {
                    Log.e(TAG, "getSchedule: Empty response body")
                    return@withContext Result.failure(Exception("Empty response body"))
                }

                try {
                    val schedule = json.decodeFromString<ScheduleResponse>(body)
                    Result.success(schedule)
                } catch (e: Exception) {
                    Log.e(TAG, "getSchedule: JSON parsing error", e)
                    Result.failure(e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getSchedule: Network error", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch league standings (team records)
     * Requires specific headers for stats.nba.com API
     */
    suspend fun getStandings(): Result<Map<Int, TeamRecord>> = withContext(ioDispatcher) {
        try {
            val request = Request.Builder()
                .url(NbaApiClient.getStandingsUrl())
                .header("origin", "https://www.nba.com")
                .header("referer", "https://www.nba.com/")
                .header("sec-ch-ua-mobile", "?0")
                .header("sec-ch-ua-platform", "\"Linux\"")
                .header("sec-fetch-dest", "empty")
                .header("sec-fetch-mode", "cors")
                .header("sec-fetch-site", "same-site")
                .header("user-agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val error = "HTTP ${response.code}: ${response.message}"
                    Log.e(TAG, "getStandings: $error")
                    return@withContext Result.failure(Exception(error))
                }

                val body = response.body?.string()
                if (body == null) {
                    Log.e(TAG, "getStandings: Empty response body")
                    return@withContext Result.failure(Exception("Empty response body"))
                }

                try {
                    val standingsResponse = json.decodeFromString<StandingsResponse>(body)

                    // Parse the standings data
                    // Array indices: [2]=TeamID, [13]=WINS, [14]=LOSSES
                    val recordsMap = mutableMapOf<Int, TeamRecord>()

                    standingsResponse.resultSets.firstOrNull()?.rowSet?.forEach { row ->
                        try {
                            val teamId = row[2].jsonPrimitive.int
                            val wins = row[13].jsonPrimitive.int
                            val losses = row[14].jsonPrimitive.int
                            recordsMap[teamId] = TeamRecord(teamId, wins, losses)
                        } catch (e: Exception) {
                            Log.w(TAG, "getStandings: Error parsing team record: ${e.message}")
                        }
                    }

                    Result.success(recordsMap)
                } catch (e: Exception) {
                    Log.e(TAG, "getStandings: JSON parsing error", e)
                    Result.failure(e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getStandings: Network error", e)
            Result.failure(e)
        }
    }
}
