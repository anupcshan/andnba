package com.gswtracker.data.repository

import com.gswtracker.data.api.NbaApiService
import com.gswtracker.data.model.Game
import com.gswtracker.data.model.GameAction
import com.gswtracker.data.model.WormPoint

/**
 * Repository for fetching and processing NBA game data
 */
class GameRepository(private val apiService: NbaApiService) {

    companion object {
        const val GSW_TEAM_CODE = "GSW"
    }

    /**
     * Get today's Warriors game, if any
     */
    suspend fun getTodaysGswGame(): Result<Game?> {
        return apiService.getScoreboard().map { response ->
            response.scoreboard.games.find { game ->
                game.homeTeam.teamTricode == GSW_TEAM_CODE ||
                        game.awayTeam.teamTricode == GSW_TEAM_CODE
            }
        }
    }

    /**
     * Get play-by-play data and convert to worm chart points
     */
    suspend fun getWormData(gameId: String): Result<List<WormPoint>> {
        return apiService.getPlayByPlay(gameId).map { response ->
            processPlayByPlayToWorm(response.game.actions)
        }
    }

    /**
     * Process play-by-play actions into worm chart data points
     * Filters for scoring events and converts to time-series data
     */
    private fun processPlayByPlayToWorm(actions: List<GameAction>): List<WormPoint> {
        return actions
            .filter { it.scoreHome != null && it.scoreAway != null }
            .mapNotNull { action ->
                try {
                    val homeScore = action.scoreHome?.toIntOrNull() ?: return@mapNotNull null
                    val awayScore = action.scoreAway?.toIntOrNull() ?: return@mapNotNull null
                    val gameTimeSeconds = calculateGameTimeSeconds(action.period, action.clock)

                    WormPoint(
                        gameTimeSeconds = gameTimeSeconds,
                        period = action.period,
                        homeScore = homeScore,
                        awayScore = awayScore,
                        scoreDiff = homeScore - awayScore
                    )
                } catch (e: Exception) {
                    null
                }
            }
            .distinctBy { it.gameTimeSeconds } // Remove duplicate time points
    }

    /**
     * Convert period and clock string to total game seconds
     * Clock format: "PT11M58.00S" (11 minutes 58 seconds remaining)
     */
    private fun calculateGameTimeSeconds(period: Int, clock: String): Int {
        val quarterLength = 12 * 60 // 12 minutes per quarter

        // Parse the ISO 8601 duration format: PT11M58.00S
        val minutesRemaining = Regex("""(\d+)M""").find(clock)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val secondsRemaining = Regex("""(\d+(?:\.\d+)?)S""").find(clock)?.groupValues?.get(1)?.toDoubleOrNull()?.toInt() ?: 0

        val timeRemainingInQuarter = minutesRemaining * 60 + secondsRemaining
        val timeElapsedInQuarter = quarterLength - timeRemainingInQuarter

        // Total time elapsed = (completed quarters * 720) + time in current quarter
        return ((period - 1) * quarterLength) + timeElapsedInQuarter
    }

    /**
     * Check if the game involves GSW
     */
    fun isGswGame(game: Game): Boolean {
        return game.homeTeam.teamTricode == GSW_TEAM_CODE ||
                game.awayTeam.teamTricode == GSW_TEAM_CODE
    }

    /**
     * Get opponent team for GSW
     */
    fun getOpponent(game: Game): String {
        return if (game.homeTeam.teamTricode == GSW_TEAM_CODE) {
            game.awayTeam.teamTricode
        } else {
            game.homeTeam.teamTricode
        }
    }

    /**
     * Check if GSW is the home team
     */
    fun isGswHome(game: Game): Boolean {
        return game.homeTeam.teamTricode == GSW_TEAM_CODE
    }
}
