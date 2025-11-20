package `in`.anupcshan.gswtracker.data.api

import `in`.anupcshan.gswtracker.data.model.BoxScoreResponse
import `in`.anupcshan.gswtracker.data.model.PlayByPlayResponse
import `in`.anupcshan.gswtracker.data.model.ScoreboardResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Service for making NBA API calls
 */
class NbaApiService(private val httpClient: OkHttpClient) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    /**
     * Fetch today's scoreboard
     */
    suspend fun getScoreboard(): Result<ScoreboardResponse> = withContext(Dispatchers.IO) {
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
     */
    suspend fun getPlayByPlay(gameId: String): Result<PlayByPlayResponse> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(NbaApiClient.getPlayByPlayUrl(gameId))
                .build()

            httpClient.newCall(request).execute().use { response ->
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
            Result.failure(e)
        }
    }

    /**
     * Fetch boxscore data for a specific game (includes arena info)
     */
    suspend fun getBoxScore(gameId: String): Result<BoxScoreResponse> = withContext(Dispatchers.IO) {
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
}
