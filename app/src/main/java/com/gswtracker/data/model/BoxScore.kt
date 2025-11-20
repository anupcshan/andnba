package com.gswtracker.data.model

import kotlinx.serialization.Serializable

@Serializable
data class BoxScoreResponse(
    val game: BoxScoreGame
)

@Serializable
data class BoxScoreGame(
    val gameId: String,
    val arena: Arena? = null
)

@Serializable
data class Arena(
    val arenaName: String,
    val arenaCity: String? = null,
    val arenaState: String? = null,
    val arenaCountry: String? = null
)
