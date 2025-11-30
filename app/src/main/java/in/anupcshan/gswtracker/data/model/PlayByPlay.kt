package `in`.anupcshan.gswtracker.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PlayByPlayResponse(
    val game: PlayByPlayGame
)

@Serializable
data class PlayByPlayGame(
    val gameId: String,
    val actions: List<GameAction>
)

@Serializable
data class GameAction(
    val actionNumber: Int,
    val period: Int,
    val clock: String, // e.g., "PT11M58.00S"
    val timeActual: String? = null,
    val scoreHome: String? = null,
    val scoreAway: String? = null,
    val actionType: String? = null,
    val shotResult: String? = null,
    val teamTricode: String? = null,
    val personId: Int? = null,
    val playerNameI: String? = null,
    val description: String? = null
)

// Processed data for worm chart
data class WormPoint(
    val gameTimeSeconds: Int, // Seconds from start of game
    val period: Int,
    val homeScore: Int,
    val awayScore: Int,
    val scoreDiff: Int // homeScore - awayScore
)

// Recent play for display
data class RecentPlay(
    val description: String,
    val teamTricode: String?,
    val clock: String,
    val period: Int,
    val gameTimeSeconds: Int
)
