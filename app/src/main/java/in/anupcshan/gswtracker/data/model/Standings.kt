package `in`.anupcshan.gswtracker.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

@Serializable
data class StandingsResponse(
    val resultSets: List<ResultSet>
)

@Serializable
data class ResultSet(
    val name: String,
    val headers: List<String>,
    val rowSet: List<JsonArray>
)

/**
 * Team record from standings API
 * Array indices: [2]=TeamID, [13]=WINS, [14]=LOSSES
 */
data class TeamRecord(
    val teamId: Int,
    val wins: Int,
    val losses: Int
)
