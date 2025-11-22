package `in`.anupcshan.gswtracker.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ScoreboardResponse(
    val scoreboard: Scoreboard
)

@Serializable
data class Scoreboard(
    val gameDate: String,
    val games: List<Game>
)

@Serializable
data class Game(
    val gameId: String,
    val gameCode: String,
    val gameStatus: Int, // 1=scheduled, 2=live, 3=final
    val gameStatusText: String,
    val period: Int,
    val gameClock: String,
    val gameTimeUTC: String? = null, // ISO 8601 format for scheduled games
    val homeTeam: Team,
    val awayTeam: Team,
    // Arena info (fetched from boxscore)
    var arenaName: String? = null
)

@Serializable
data class Team(
    val teamId: Int,
    val teamName: String,
    val teamCity: String,
    val teamTricode: String, // e.g., "GSW"
    val score: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val periods: List<Period> = emptyList()
)

@Serializable
data class Period(
    val period: Int,
    val periodType: String = "REGULAR",
    val score: Int
)

// Schedule API models
@Serializable
data class ScheduleResponse(
    val leagueSchedule: LeagueSchedule
)

@Serializable
data class LeagueSchedule(
    val seasonYear: String,
    val gameDates: List<GameDate>
)

@Serializable
data class GameDate(
    val gameDate: String, // Format: "MM/DD/YYYY HH:MM:SS"
    val games: List<ScheduledGame>
)

@Serializable
data class ScheduledGame(
    val gameId: String,
    val gameCode: String,
    val gameLabel: String? = null,
    val gameDateTimeUTC: String, // ISO 8601 format
    val homeTeam: ScheduledTeam,
    val awayTeam: ScheduledTeam,
    val arenaName: String? = null,
    val arenaCity: String? = null,
    val arenaState: String? = null
)

@Serializable
data class ScheduledTeam(
    val teamId: Int,
    val teamName: String? = null,
    val teamCity: String? = null,
    val teamTricode: String? = null
)
