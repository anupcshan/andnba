package `in`.anupcshan.gswtracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.anupcshan.gswtracker.data.model.GameState
import `in`.anupcshan.gswtracker.data.repository.GameRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing game state and data fetching
 */
class GameViewModel(private val repository: GameRepository) : ViewModel() {

    private val _gameState = MutableStateFlow<GameState>(GameState.Loading)
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private var pollingJob: Job? = null
    private var lastSeenPeriod: Int = 0
    private var currentGameId: String? = null

    init {
        refreshGame()
    }

    /**
     * Manually refresh game data
     */
    fun refreshGame() {
        viewModelScope.launch {
            fetchGameData()
        }
    }

    /**
     * Fetch today's game and determine state
     */
    private suspend fun fetchGameData() {
        _gameState.value = GameState.Loading

        repository.getTodaysGswGame()
            .onSuccess { game ->
                if (game == null) {
                    _gameState.value = GameState.NoGameToday(nextGame = null)
                    stopPolling()
                } else {
                    currentGameId = game.gameId
                    handleGameState(game)
                }
            }
            .onFailure { error ->
                _gameState.value = GameState.Error(
                    error.message ?: "Failed to load game data"
                )
                stopPolling()
            }
    }

    /**
     * Determine game state based on status
     */
    private suspend fun handleGameState(game: `in`.anupcshan.gswtracker.data.model.Game) {
        when (game.gameStatus) {
            1 -> {
                // Game scheduled
                _gameState.value = GameState.GameScheduled(game)
                stopPolling()
            }
            2 -> {
                // Game live
                fetchWormDataIfNeeded(game)
                _gameState.value = GameState.GameLive(
                    game = game,
                    wormData = (gameState.value as? GameState.GameLive)?.wormData ?: emptyList()
                )
                startPollingIfNeeded()
                checkForQuarterTransition(game)
            }
            3 -> {
                // Game final
                fetchWormDataForFinalGame(game)
                stopPolling()
            }
            else -> {
                _gameState.value = GameState.Error("Unknown game status: ${game.gameStatus}")
                stopPolling()
            }
        }
    }

    /**
     * Fetch worm data if quarter has changed
     */
    private suspend fun fetchWormDataIfNeeded(game: `in`.anupcshan.gswtracker.data.model.Game) {
        if (game.period > lastSeenPeriod) {
            // New quarter started - fetch updated play-by-play
            fetchWormData(game)
            lastSeenPeriod = game.period
        }
    }

    /**
     * Fetch worm data for completed game
     */
    private suspend fun fetchWormDataForFinalGame(game: `in`.anupcshan.gswtracker.data.model.Game) {
        val isGswHome = repository.isGswHome(game)
        repository.getWormData(game.gameId, isGswHome)
            .onSuccess { wormData ->
                _gameState.value = GameState.GameFinal(
                    game = game,
                    wormData = wormData
                )
            }
            .onFailure { error ->
                // Show game as final even if worm data fails
                _gameState.value = GameState.GameFinal(
                    game = game,
                    wormData = emptyList()
                )
            }
    }

    /**
     * Fetch worm data for live game
     */
    private suspend fun fetchWormData(game: `in`.anupcshan.gswtracker.data.model.Game) {
        val isGswHome = repository.isGswHome(game)
        repository.getWormData(game.gameId, isGswHome)
            .onSuccess { wormData ->
                val currentState = _gameState.value
                if (currentState is GameState.GameLive) {
                    _gameState.value = currentState.copy(wormData = wormData)
                }
            }
            .onFailure {
                // Silently fail - keep showing live game without worm data
            }
    }

    /**
     * Check if quarter has transitioned
     */
    private fun checkForQuarterTransition(game: `in`.anupcshan.gswtracker.data.model.Game) {
        if (game.period > lastSeenPeriod) {
            lastSeenPeriod = game.period
        }
    }

    /**
     * Start polling for live game updates
     */
    private fun startPollingIfNeeded() {
        if (pollingJob?.isActive == true) return

        pollingJob = viewModelScope.launch {
            while (true) {
                delay(60_000) // 60 seconds
                fetchGameData()
            }
        }
    }

    /**
     * Stop polling
     */
    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }
}
