package `in`.anupcshan.gswtracker.data.repository

import android.util.Log
import `in`.anupcshan.gswtracker.data.api.NbaApiService
import `in`.anupcshan.gswtracker.data.model.Game
import `in`.anupcshan.gswtracker.data.model.GameAction
import `in`.anupcshan.gswtracker.data.model.RecentPlay
import `in`.anupcshan.gswtracker.data.model.Conference
import `in`.anupcshan.gswtracker.data.model.ConferenceStandings
import `in`.anupcshan.gswtracker.data.model.ConferenceTeams
import `in`.anupcshan.gswtracker.data.model.ScheduledGame
import `in`.anupcshan.gswtracker.data.model.Team
import `in`.anupcshan.gswtracker.data.model.TeamStanding
import `in`.anupcshan.gswtracker.data.model.WormPoint
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Container for play-by-play derived data
 */
data class PlayByPlayData(
    val wormData: List<WormPoint>,
    val recentPlays: List<RecentPlay>
)

/**
 * Repository for fetching and processing NBA game data
 */
class GameRepository(
    private val apiService: NbaApiService,
    private val currentTimeProvider: () -> Instant = { Instant.now() }
) {

    companion object {
        private const val TAG = "GameRepository"

        /**
         * Format UTC time to local time string
         * @param utcTimeStr ISO 8601 UTC time string
         * @return Formatted local time (e.g., "7:00 PM PST")
         */
        fun formatGameTime(utcTimeStr: String?): String {
            if (utcTimeStr == null) return "TBD"

            return try {
                val instant = Instant.parse(utcTimeStr)
                val zonedDateTime = instant.atZone(ZoneId.systemDefault())
                val formatter = DateTimeFormatter.ofPattern("h:mm a z")
                zonedDateTime.format(formatter)
            } catch (e: Exception) {
                "TBD"
            }
        }
    }

    /**
     * Get today's game for specified team, if any
     * Also fetches arena information from boxscore
     * @param teamTricode The team's 3-letter code (e.g., "GSW", "LAL")
     * @return Pair of (Scoreboard, Game?) - includes scoreboard for date checking
     */
    suspend fun getTodaysTeamGame(teamTricode: String): Result<Pair<`in`.anupcshan.gswtracker.data.model.Scoreboard, Game?>> {
        return apiService.getScoreboard().mapCatching { response ->
            val game = response.scoreboard.games.find { game ->
                game.homeTeam.teamTricode == teamTricode ||
                        game.awayTeam.teamTricode == teamTricode
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
     * Get play-by-play data and convert to worm chart points and recent plays
     * @param gameId The game ID
     * @param isGswHome Whether GSW is the home team (for correct score differential calculation)
     * @param forceRefresh If true, bypasses cache for live game updates
     */
    suspend fun getPlayByPlayData(gameId: String, isGswHome: Boolean, forceRefresh: Boolean = false): Result<PlayByPlayData> {
        return apiService.getPlayByPlay(gameId, forceRefresh).map { response ->
            PlayByPlayData(
                wormData = processPlayByPlayToWorm(response.game.actions, isGswHome),
                recentPlays = extractRecentPlays(response.game.actions)
            )
        }
    }

    /**
     * Try to restore play-by-play data from HTTP cache (no network call)
     * Returns PlayByPlayData and lastFetchedPeriod if cache hit, null otherwise
     * This allows restoring state across app restarts without refetching
     */
    suspend fun tryRestoreFromCache(gameId: String, isGswHome: Boolean): Result<Pair<PlayByPlayData, Int>?> {
        return apiService.getPlayByPlayFromCache(gameId).map { response ->
            if (response == null) {
                // Cache miss
                null
            } else {
                val data = PlayByPlayData(
                    wormData = processPlayByPlayToWorm(response.game.actions, isGswHome),
                    recentPlays = extractRecentPlays(response.game.actions)
                )
                val lastPeriod = response.game.actions.maxOfOrNull { it.period } ?: 0
                data to lastPeriod
            }
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
     * Extract all plays with descriptions from play-by-play data
     */
    private fun extractRecentPlays(actions: List<GameAction>): List<RecentPlay> {
        return actions
            .filter { !it.description.isNullOrBlank() }
            .reversed() // Most recent first
            .map { action ->
                RecentPlay(
                    description = action.description!!,
                    teamTricode = action.teamTricode,
                    clock = formatClock(action.clock),
                    period = action.period,
                    gameTimeSeconds = calculateGameTimeSeconds(action.period, action.clock)
                )
            }
    }

    /**
     * Format clock string from ISO duration to readable format
     * e.g., "PT11M58.00S" -> "11:58"
     */
    private fun formatClock(clock: String): String {
        val minutes = Regex("""(\d+)M""").find(clock)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val seconds = Regex("""(\d+(?:\.\d+)?)S""").find(clock)?.groupValues?.get(1)?.toDoubleOrNull()?.toInt() ?: 0
        return "%d:%02d".format(minutes, seconds)
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
     * Check if a specific team is the home team
     */
    fun isTeamHome(game: Game, teamTricode: String): Boolean {
        return game.homeTeam.teamTricode == teamTricode
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
     * Get the next upcoming game for specified team from the schedule
     * @param teamTricode The team's 3-letter code (e.g., "GSW", "LAL")
     * @return ScheduledGame or null if no upcoming game found
     */
    suspend fun getNextTeamGame(teamTricode: String): Result<ScheduledGame?> {
        return apiService.getSchedule().map { response ->
            val now = currentTimeProvider()

            val teamGames = response.leagueSchedule.gameDates
                .flatMap { gameDate ->
                    gameDate.games.filter { game ->
                        // Skip games with incomplete team data (TBD playoff matchups)
                        game.homeTeam.teamTricode != null && game.awayTeam.teamTricode != null &&
                        (game.homeTeam.teamTricode == teamTricode ||
                                game.awayTeam.teamTricode == teamTricode)
                    }.map { game ->
                        game to parseGameDateTime(game.gameDateTimeUTC)
                    }
                }

            val futureGames = teamGames.filter { (_, dateTime) ->
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
     * Team records come directly from the schedule API
     */
    fun scheduledGameToGame(scheduledGame: ScheduledGame): Game {
        return Game(
            gameId = scheduledGame.gameId,
            gameCode = scheduledGame.gameCode,
            gameStatus = 1, // Scheduled
            gameStatusText = formatGameTime(scheduledGame.gameDateTimeUTC),
            period = 0,
            gameClock = "",
            gameTimeUTC = scheduledGame.gameDateTimeUTC,
            homeTeam = Team(
                teamId = scheduledGame.homeTeam.teamId,
                teamName = scheduledGame.homeTeam.teamName ?: "TBD",
                teamCity = scheduledGame.homeTeam.teamCity ?: "",
                teamTricode = scheduledGame.homeTeam.teamTricode ?: "TBD",
                score = 0,
                wins = scheduledGame.homeTeam.wins,
                losses = scheduledGame.homeTeam.losses
            ),
            awayTeam = Team(
                teamId = scheduledGame.awayTeam.teamId,
                teamName = scheduledGame.awayTeam.teamName ?: "TBD",
                teamCity = scheduledGame.awayTeam.teamCity ?: "",
                teamTricode = scheduledGame.awayTeam.teamTricode ?: "TBD",
                score = 0,
                wins = scheduledGame.awayTeam.wins,
                losses = scheduledGame.awayTeam.losses
            ),
            arenaName = scheduledGame.arenaName
        )
    }

    /**
     * Calculate conference standings from schedule data
     * Extracts the most recent record for each team
     */
    suspend fun getConferenceStandings(): Result<ConferenceStandings> {
        return apiService.getSchedule().map { response ->
            // Collect most recent record for each team
            val teamRecords = mutableMapOf<Int, Pair<String, Pair<Int, Int>>>() // teamId -> (tricode, wins, losses)

            // Go through all games and extract team records
            // Later games in the schedule have more current records
            response.leagueSchedule.gameDates.forEach { gameDate ->
                gameDate.games.forEach { game ->
                    // Only use records from completed games or games with actual records
                    if (game.homeTeam.wins + game.homeTeam.losses > 0) {
                        teamRecords[game.homeTeam.teamId] = Pair(
                            game.homeTeam.teamTricode ?: "???",
                            Pair(game.homeTeam.wins, game.homeTeam.losses)
                        )
                    }
                    if (game.awayTeam.wins + game.awayTeam.losses > 0) {
                        teamRecords[game.awayTeam.teamId] = Pair(
                            game.awayTeam.teamTricode ?: "???",
                            Pair(game.awayTeam.wins, game.awayTeam.losses)
                        )
                    }
                }
            }

            // Build conference standings
            val westernTeams = mutableListOf<TeamStanding>()
            val easternTeams = mutableListOf<TeamStanding>()

            teamRecords.forEach { (teamId, data) ->
                val (tricode, record) = data
                val (wins, losses) = record
                val conference = ConferenceTeams.getConference(teamId) ?: return@forEach

                val standing = TeamStanding(
                    teamId = teamId,
                    teamTricode = tricode,
                    wins = wins,
                    losses = losses,
                    rank = 0, // Will be set after sorting
                    gamesBack = 0f, // Will be calculated after sorting
                    conference = conference
                )

                when (conference) {
                    Conference.WESTERN -> westernTeams.add(standing)
                    Conference.EASTERN -> easternTeams.add(standing)
                }
            }

            // Sort by win percentage (descending), then by wins (descending) for tiebreaker
            val sortedWestern = westernTeams
                .sortedWith(compareByDescending<TeamStanding> { it.winPct }.thenByDescending { it.wins })
                .mapIndexed { index, team ->
                    team.copy(rank = index + 1)
                }
                .let { teams ->
                    if (teams.isEmpty()) return@let teams
                    val firstPlaceWins = teams.first().wins
                    val firstPlaceLosses = teams.first().losses
                    teams.map { team ->
                        team.copy(gamesBack = calculateGamesBack(firstPlaceWins, firstPlaceLosses, team.wins, team.losses))
                    }
                }

            val sortedEastern = easternTeams
                .sortedWith(compareByDescending<TeamStanding> { it.winPct }.thenByDescending { it.wins })
                .mapIndexed { index, team ->
                    team.copy(rank = index + 1)
                }
                .let { teams ->
                    if (teams.isEmpty()) return@let teams
                    val firstPlaceWins = teams.first().wins
                    val firstPlaceLosses = teams.first().losses
                    teams.map { team ->
                        team.copy(gamesBack = calculateGamesBack(firstPlaceWins, firstPlaceLosses, team.wins, team.losses))
                    }
                }

            ConferenceStandings(
                western = sortedWestern,
                eastern = sortedEastern
            )
        }
    }

    /**
     * Get standing info for a specific team
     */
    suspend fun getTeamStanding(teamId: Int): Result<TeamStanding?> {
        return getConferenceStandings().map { standings ->
            standings.western.find { it.teamId == teamId }
                ?: standings.eastern.find { it.teamId == teamId }
        }
    }

    /**
     * Calculate games back from another team
     * GB = ((W1 - W2) + (L2 - L1)) / 2
     */
    private fun calculateGamesBack(refWins: Int, refLosses: Int, teamWins: Int, teamLosses: Int): Float {
        return ((refWins - teamWins) + (teamLosses - refLosses)) / 2f
    }

    /**
     * Get games back from the next better position
     */
    fun getGamesBackFromPosition(standings: List<TeamStanding>, teamRank: Int, targetRank: Int): Float {
        if (targetRank < 1 || targetRank >= teamRank || teamRank > standings.size) return 0f
        val targetTeam = standings.getOrNull(targetRank - 1) ?: return 0f
        val currentTeam = standings.getOrNull(teamRank - 1) ?: return 0f
        return calculateGamesBack(targetTeam.wins, targetTeam.losses, currentTeam.wins, currentTeam.losses)
    }
}
