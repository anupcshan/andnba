package `in`.anupcshan.gswtracker.data.repository

import android.util.Log
import `in`.anupcshan.gswtracker.data.api.NbaApiService
import `in`.anupcshan.gswtracker.data.model.Game
import `in`.anupcshan.gswtracker.data.model.GameAction
import `in`.anupcshan.gswtracker.data.model.ScheduledGame
import `in`.anupcshan.gswtracker.data.model.Team
import `in`.anupcshan.gswtracker.data.model.WormPoint
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Repository for fetching and processing NBA game data
 */
class GameRepository(private val apiService: NbaApiService) {

    companion object {
        private const val TAG = "GameRepository"
        const val GSW_TEAM_CODE = "GSW"
    }

    /**
     * Get today's Warriors game, if any
     * Also fetches arena information from boxscore
     * @return Pair of (Scoreboard, Game?) - includes scoreboard for date checking
     */
    suspend fun getTodaysGswGame(): Result<Pair<`in`.anupcshan.gswtracker.data.model.Scoreboard, Game?>> {
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

            response.scoreboard to game
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

    /**
     * Check if a completed game is stale (past 10am the day after the game)
     * @param gameDate The game date string from scoreboard (format: "2025-11-19")
     */
    fun isGameStale(gameDate: String): Boolean {
        return try {
            val gameDateParsed = LocalDate.parse(gameDate)
            val cutoffDateTime = ZonedDateTime.of(
                gameDateParsed.plusDays(1),
                LocalTime.of(10, 0),
                ZoneId.systemDefault()
            )
            val now = ZonedDateTime.now()
            now.isAfter(cutoffDateTime)
        } catch (e: Exception) {
            false // If parsing fails, don't consider it stale
        }
    }

    /**
     * Get the next upcoming Warriors game from the schedule
     * @return ScheduledGame or null if no upcoming game found
     */
    suspend fun getNextGswGame(): Result<ScheduledGame?> {
        return apiService.getSchedule().map { response ->
            val now = Instant.now()

            val gswGames = response.leagueSchedule.gameDates
                .flatMap { gameDate ->
                    gameDate.games.filter { game ->
                        // Skip games with incomplete team data (TBD playoff matchups)
                        game.homeTeam.teamTricode != null && game.awayTeam.teamTricode != null &&
                        (game.homeTeam.teamTricode == GSW_TEAM_CODE ||
                                game.awayTeam.teamTricode == GSW_TEAM_CODE)
                    }.map { game ->
                        game to parseGameDateTime(game.gameDateTimeUTC)
                    }
                }

            val futureGames = gswGames.filter { (_, dateTime) ->
                    dateTime != null && dateTime.isAfter(now)
                }

            futureGames.minByOrNull { (_, dateTime) -> dateTime!! }?.first
        }
    }

    /**
     * Parse ISO 8601 datetime string to Instant
     */
    private fun parseGameDateTime(dateTimeStr: String): Instant? {
        return try {
            Instant.parse(dateTimeStr)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Convert ScheduledGame to Game for displaying in UI
     */
    fun scheduledGameToGame(scheduledGame: ScheduledGame): Game {
        // Format game time for display
        val gameTime = try {
            val instant = Instant.parse(scheduledGame.gameDateTimeUTC)
            val zonedDateTime = instant.atZone(ZoneId.systemDefault())
            val formatter = DateTimeFormatter.ofPattern("h:mm a z")
            zonedDateTime.format(formatter)
        } catch (e: Exception) {
            "TBD"
        }

        return Game(
            gameId = scheduledGame.gameId,
            gameCode = scheduledGame.gameCode,
            gameStatus = 1, // Scheduled
            gameStatusText = gameTime,
            period = 0,
            gameClock = "",
            homeTeam = Team(
                teamId = scheduledGame.homeTeam.teamId,
                teamName = scheduledGame.homeTeam.teamName ?: "TBD",
                teamCity = scheduledGame.homeTeam.teamCity ?: "",
                teamTricode = scheduledGame.homeTeam.teamTricode ?: "TBD",
                score = 0,
                wins = 0,
                losses = 0
            ),
            awayTeam = Team(
                teamId = scheduledGame.awayTeam.teamId,
                teamName = scheduledGame.awayTeam.teamName ?: "TBD",
                teamCity = scheduledGame.awayTeam.teamCity ?: "",
                teamTricode = scheduledGame.awayTeam.teamTricode ?: "TBD",
                score = 0,
                wins = 0,
                losses = 0
            ),
            arenaName = scheduledGame.arenaName
        )
    }
}
