package com.gswtracker.data.model

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
    val homeTeam: Team,
    val awayTeam: Team
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
