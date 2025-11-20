package `in`.anupcshan.gswtracker.data.repository

import `in`.anupcshan.gswtracker.data.api.NbaApiService
import `in`.anupcshan.gswtracker.data.model.Game
import `in`.anupcshan.gswtracker.data.model.GameAction
import `in`.anupcshan.gswtracker.data.model.WormPoint

/**
 * Repository for fetching and processing NBA game data
 */
class GameRepository(private val apiService: NbaApiService) {

    companion object {
        const val GSW_TEAM_CODE = "GSW"
    }

    /**
     * Get today's Warriors game, if any
     * Also fetches arena information from boxscore
     */
    suspend fun getTodaysGswGame(): Result<Game?> {
        return apiService.getScoreboard().mapCatching { response ->
            val game = response.scoreboard.games.find { game ->
                game.homeTeam.teamTricode == GSW_TEAM_CODE ||
                        game.awayTeam.teamTricode == GSW_TEAM_CODE
            }

            // Fetch arena info if game exists
            if (game != null) {
                apiService.getBoxScore(game.gameId).onSuccess { boxScore ->
                    game.arenaName = boxScore.game.arena?.arenaName
                }
            }

            game
        }
    }

    /**
     * Get play-by-play data and convert to worm chart points
     * @param gameId The game ID
     * @param isGswHome Whether GSW is the home team (for correct score differential calculation)
     */
    suspend fun getWormData(gameId: String, isGswHome: Boolean): Result<List<WormPoint>> {
        return apiService.getPlayByPlay(gameId).map { response ->
            processPlayByPlayToWorm(response.game.actions, isGswHome)
        }
    }

    /**
     * Process play-by-play actions into worm chart data points
     * Filters for scoring events and converts to time-series data
     * @param isGswHome Whether GSW is the home team (affects score differential calculation)
     */
    private fun processPlayByPlayToWorm(actions: List<GameAction>, isGswHome: Boolean): List<WormPoint> {
        return actions
            .filter { it.scoreHome != null && it.scoreAway != null }
            .mapNotNull { action ->
                try {
                    val homeScore = action.scoreHome?.toIntOrNull() ?: return@mapNotNull null
                    val awayScore = action.scoreAway?.toIntOrNull() ?: return@mapNotNull null
                    val gameTimeSeconds = calculateGameTimeSeconds(action.period, action.clock)

                    // Calculate score differential from GSW's perspective
                    val gswScore = if (isGswHome) homeScore else awayScore
                    val oppScore = if (isGswHome) awayScore else homeScore

                    WormPoint(
                        gameTimeSeconds = gameTimeSeconds,
                        period = action.period,
                        homeScore = homeScore,
                        awayScore = awayScore,
                        scoreDiff = gswScore - oppScore // Always GSW - Opponent
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
