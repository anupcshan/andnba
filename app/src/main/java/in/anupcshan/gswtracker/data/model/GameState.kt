package `in`.anupcshan.gswtracker.data.model

/**
 * Represents the different states of the game screen
 */
sealed class GameState {
    object Loading : GameState()

    data class NoGameToday(val nextGame: Game?) : GameState()

    data class GameScheduled(val game: Game) : GameState()

    data class GameLive(
        val game: Game,
        val wormData: List<WormPoint> = emptyList()
    ) : GameState()

    data class GameFinal(
        val game: Game,
        val wormData: List<WormPoint>
    ) : GameState()

    data class Error(val message: String) : GameState()
}
