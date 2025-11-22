package `in`.anupcshan.gswtracker.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.anupcshan.gswtracker.data.model.GameState
import `in`.anupcshan.gswtracker.data.model.NBATeam
import `in`.anupcshan.gswtracker.data.model.NBATeams
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

    private val _selectedTeam = MutableStateFlow(NBATeams.DEFAULT_TEAM)
    val selectedTeam: StateFlow<NBATeam> = _selectedTeam.asStateFlow()

    private val _isPolling = MutableStateFlow(false)
    val isPolling: StateFlow<Boolean> = _isPolling.asStateFlow()

    private val _lastUpdateTime = MutableStateFlow<Long?>(null)
    val lastUpdateTime: StateFlow<Long?> = _lastUpdateTime.asStateFlow()

    private var pollingJob: Job? = null
    private var currentGameId: String? = null

    companion object {
        private const val TAG = "GameViewModel"
    }

    init {
        refreshGame()
    }

    /**
     * Change the selected team and refresh game data
     */
    fun selectTeam(team: NBATeam) {
        if (_selectedTeam.value.tricode != team.tricode) {
            _selectedTeam.value = team
            refreshGame()
        }
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
        _lastUpdateTime.value = System.currentTimeMillis()
        val teamTricode = _selectedTeam.value.tricode

        repository.getTodaysTeamGame(teamTricode)
            .onSuccess { todaysGameResult ->
                val scoreboard = todaysGameResult.first
                val game = todaysGameResult.second

                when {
                    // No game scheduled today
                    game == null -> {
                        fetchAndShowNextGame()
                    }
                    // Game is final and it's past 10am the next day - show next game instead
                    game.gameStatus == 3 && repository.isGameStale(scoreboard.gameDate) -> {
                        fetchAndShowNextGame()
                    }
                    // Game exists and isn't stale - show it normally
                    else -> {
                        currentGameId = game.gameId
                        handleGameState(game)
                    }
                }
            }
            .onFailure { error ->
                Log.e(TAG, "fetchGameData: Error fetching game", error)
                _gameState.value = GameState.Error(
                    error.message ?: "Failed to load game data"
                )
                stopPolling()
            }
    }

    /**
     * Fetch next upcoming game and show it
     */
    private suspend fun fetchAndShowNextGame() {
        val teamTricode = _selectedTeam.value.tricode

        repository.getNextTeamGame(teamTricode)
            .onSuccess { scheduledGame ->
                if (scheduledGame != null) {
                    val game = repository.scheduledGameToGame(scheduledGame)
                    _gameState.value = GameState.GameScheduled(game)
                } else {
                    _gameState.value = GameState.NoGameToday(nextGame = null)
                }
                stopPolling()
            }
            .onFailure { error ->
                // If we can't fetch schedule, just show no game
                Log.e(TAG, "fetchAndShowNextGame: Error fetching schedule", error)
                _gameState.value = GameState.NoGameToday(nextGame = null)
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
                // Game live - try to restore from cache first
                val teamTricode = _selectedTeam.value.tricode
                val isTeamHome = repository.isTeamHome(game, teamTricode)

                // Try restoring from HTTP cache (survives process restart)
                val cached = repository.tryRestoreWormDataFromCache(game.gameId, isTeamHome)
                    .getOrNull()

                val (wormData, lastFetched) = if (cached != null) {
                    // Cache hit - use restored data
                    cached.first to cached.second
                } else {
                    // Cache miss - start fresh
                    val currentState = gameState.value
                    val currentWormData = (currentState as? GameState.GameLive)?.wormData ?: emptyList()
                    val currentLastFetched = (currentState as? GameState.GameLive)?.lastFetchedPeriod ?: 0
                    currentWormData to currentLastFetched
                }

                _gameState.value = GameState.GameLive(
                    game = game,
                    wormData = wormData,
                    lastFetchedPeriod = lastFetched
                )

                fetchWormDataIfNeeded(game)
                startPollingIfNeeded()
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
     * Fetch worm data if period has changed since last fetch
     * This ensures we have worm data even when opening the app mid-game
     */
    private suspend fun fetchWormDataIfNeeded(game: `in`.anupcshan.gswtracker.data.model.Game) {
        val currentState = gameState.value
        val lastFetched = (currentState as? GameState.GameLive)?.lastFetchedPeriod ?: 0

        if (game.period != lastFetched && game.period > 0) {
            // Period changed or first fetch - get updated play-by-play
            fetchWormData(game)
        }
    }

    /**
     * Fetch worm data for completed game
     */
    private suspend fun fetchWormDataForFinalGame(game: `in`.anupcshan.gswtracker.data.model.Game) {
        val teamTricode = _selectedTeam.value.tricode
        val isTeamHome = repository.isTeamHome(game, teamTricode)
        repository.getWormData(game.gameId, isTeamHome)
            .onSuccess { wormData ->
                _gameState.value = GameState.GameFinal(
                    game = game,
                    wormData = wormData,
                    lastFetchedPeriod = game.period
                )
            }
            .onFailure { error ->
                // Show game as final even if worm data fails
                _gameState.value = GameState.GameFinal(
                    game = game,
                    wormData = emptyList(),
                    lastFetchedPeriod = 0
                )
            }
    }

    /**
     * Fetch worm data for live game
     */
    private suspend fun fetchWormData(game: `in`.anupcshan.gswtracker.data.model.Game) {
        val teamTricode = _selectedTeam.value.tricode
        val isTeamHome = repository.isTeamHome(game, teamTricode)
        repository.getWormData(game.gameId, isTeamHome)
            .onSuccess { wormData ->
                val currentState = _gameState.value
                if (currentState is GameState.GameLive) {
                    _gameState.value = currentState.copy(
                        wormData = wormData,
                        lastFetchedPeriod = game.period
                    )
                }
            }
            .onFailure {
                // Silently fail - keep showing live game without worm data
            }
    }

    /**
     * Start polling for live game updates
     */
    private fun startPollingIfNeeded() {
        if (pollingJob?.isActive == true) return

        _isPolling.value = true
        pollingJob = viewModelScope.launch {
            while (true) {
                delay(15_000) // 15 seconds
                fetchGameData()
            }
        }
    }

    /**
     * Stop polling
     */
    private fun stopPolling() {
        _isPolling.value = false
        _lastUpdateTime.value = null
        pollingJob?.cancel()
        pollingJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }
}
