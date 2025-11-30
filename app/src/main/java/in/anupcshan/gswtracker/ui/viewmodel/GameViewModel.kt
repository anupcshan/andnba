package `in`.anupcshan.gswtracker.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.anupcshan.gswtracker.data.api.DataUsageTracker
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

    private val _liveTeams = MutableStateFlow<Set<String>>(emptySet())
    val liveTeams: StateFlow<Set<String>> = _liveTeams.asStateFlow()

    val dataUsage: StateFlow<Long> = DataUsageTracker.bytesUsed

    fun resetDataUsage() {
        DataUsageTracker.reset()
    }

    private var pollingJob: Job? = null
    private var currentGameId: String? = null
    private var lastKnownScore: Pair<Int, Int>? = null // (homeScore, awayScore)

    companion object {
        private const val TAG = "GameViewModel"
    }

    init {
        viewModelScope.launch {
            fetchGameData(showLoading = true)
        }
    }

    /**
     * Change the selected team and refresh game data
     */
    fun selectTeam(team: NBATeam) {
        if (_selectedTeam.value.tricode != team.tricode) {
            _selectedTeam.value = team
            lastKnownScore = null
            refreshGame()
        }
    }

    /**
     * Manually refresh game data
     */
    fun refreshGame() {
        viewModelScope.launch {
            fetchGameData(showLoading = true)
        }
    }

    /**
     * Fetch today's game and determine state
     * @param showLoading If true, shows loading state (used for initial load/refresh)
     */
    private suspend fun fetchGameData(showLoading: Boolean = false) {
        if (showLoading) {
            _gameState.value = GameState.Loading
        }
        _lastUpdateTime.value = System.currentTimeMillis()
        val teamTricode = _selectedTeam.value.tricode

        repository.getTodaysTeamGame(teamTricode)
            .onSuccess { todaysGameResult ->
                val scoreboard = todaysGameResult.first
                val game = todaysGameResult.second

                // Update live teams from all games in scoreboard
                _liveTeams.value = scoreboard.games
                    .filter { it.gameStatus == 2 }
                    .flatMap { listOf(it.homeTeam.teamTricode, it.awayTeam.teamTricode) }
                    .toSet()

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
                // Only show error on initial load, not during polling
                // This prevents transient network errors from disrupting the UI
                if (showLoading || _gameState.value is GameState.Loading) {
                    _gameState.value = GameState.Error(
                        error.message ?: "Failed to load game data"
                    )
                    stopPolling()
                }
                // During polling, silently ignore errors and keep previous state
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
                val cached = repository.tryRestoreFromCache(game.gameId, isTeamHome)
                    .getOrNull()

                val (wormData, recentPlays, lastFetched) = if (cached != null) {
                    // Cache hit - use restored data
                    Triple(cached.first.wormData, cached.first.recentPlays, cached.second)
                } else {
                    // Cache miss - start fresh
                    val currentState = gameState.value
                    val currentWormData = (currentState as? GameState.GameLive)?.wormData ?: emptyList()
                    val currentRecentPlays = (currentState as? GameState.GameLive)?.recentPlays ?: emptyList()
                    val currentLastFetched = (currentState as? GameState.GameLive)?.lastFetchedPeriod ?: 0
                    Triple(currentWormData, currentRecentPlays, currentLastFetched)
                }

                _gameState.value = GameState.GameLive(
                    game = game,
                    wormData = wormData,
                    recentPlays = recentPlays,
                    lastFetchedPeriod = lastFetched
                )

                fetchPlayByPlayIfNeeded(game)
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
     * Fetch play-by-play data if period or score has changed
     * This ensures recent plays update on every scoring event
     */
    private suspend fun fetchPlayByPlayIfNeeded(game: `in`.anupcshan.gswtracker.data.model.Game) {
        val currentState = gameState.value
        val lastFetched = (currentState as? GameState.GameLive)?.lastFetchedPeriod ?: 0
        val currentScore = game.homeTeam.score to game.awayTeam.score
        val scoreChanged = lastKnownScore != currentScore

        if ((game.period != lastFetched && game.period > 0) || scoreChanged) {
            lastKnownScore = currentScore
            fetchPlayByPlayData(game)
        }
    }

    /**
     * Fetch worm data for completed game
     */
    private suspend fun fetchWormDataForFinalGame(game: `in`.anupcshan.gswtracker.data.model.Game) {
        val teamTricode = _selectedTeam.value.tricode
        val isTeamHome = repository.isTeamHome(game, teamTricode)

        // Fetch next game info
        val nextGameResult = repository.getNextTeamGame(teamTricode)
        val nextGame = nextGameResult.getOrNull()?.let { repository.scheduledGameToGame(it) }

        repository.getPlayByPlayData(game.gameId, isTeamHome)
            .onSuccess { data ->
                _gameState.value = GameState.GameFinal(
                    game = game,
                    wormData = data.wormData,
                    lastFetchedPeriod = game.period,
                    nextGame = nextGame
                )
            }
            .onFailure { _ ->
                // Show game as final even if worm data fails
                _gameState.value = GameState.GameFinal(
                    game = game,
                    wormData = emptyList(),
                    lastFetchedPeriod = 0,
                    nextGame = nextGame
                )
            }
    }

    /**
     * Fetch play-by-play data for live game
     */
    private suspend fun fetchPlayByPlayData(game: `in`.anupcshan.gswtracker.data.model.Game) {
        val teamTricode = _selectedTeam.value.tricode
        val isTeamHome = repository.isTeamHome(game, teamTricode)
        repository.getPlayByPlayData(game.gameId, isTeamHome, forceRefresh = true)
            .onSuccess { data ->
                val currentState = _gameState.value
                if (currentState is GameState.GameLive) {
                    _gameState.value = currentState.copy(
                        wormData = data.wormData,
                        recentPlays = data.recentPlays,
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
        lastKnownScore = null
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }
}
