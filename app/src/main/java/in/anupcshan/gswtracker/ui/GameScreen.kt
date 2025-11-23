package `in`.anupcshan.gswtracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.anupcshan.gswtracker.data.model.Game
import `in`.anupcshan.gswtracker.data.model.GameState
import `in`.anupcshan.gswtracker.data.model.NBATeam
import `in`.anupcshan.gswtracker.data.model.NBATeams
import `in`.anupcshan.gswtracker.data.repository.GameRepository
import `in`.anupcshan.gswtracker.ui.components.WormChart
import `in`.anupcshan.gswtracker.ui.viewmodel.GameViewModel
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(viewModel: GameViewModel) {
    val gameState by viewModel.gameState.collectAsState()
    val selectedTeam by viewModel.selectedTeam.collectAsState()
    val isPolling by viewModel.isPolling.collectAsState()
    val lastUpdateTime by viewModel.lastUpdateTime.collectAsState()
    val isRefreshing = gameState is GameState.Loading

    Column(modifier = Modifier.fillMaxSize()) {
        // Team selector dropdown
        TeamSelector(
            selectedTeam = selectedTeam,
            onTeamSelected = { viewModel.selectTeam(it) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        SwipeRefresh(
            state = rememberSwipeRefreshState(isRefreshing),
            onRefresh = { viewModel.refreshGame() }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                when (val state = gameState) {
                    is GameState.Loading -> LoadingView()
                    is GameState.NoGameToday -> NoGameView(state.nextGame, selectedTeam)
                    is GameState.GameScheduled -> ScheduledGameView(state.game, selectedTeam)
                    is GameState.GameLive -> LiveGameView(state.game, state.wormData, selectedTeam)
                    is GameState.GameFinal -> FinalGameView(state.game, state.wormData, state.nextGame, selectedTeam)
                    is GameState.Error -> ErrorView(state.message) { viewModel.refreshGame() }
                }

                // Polling indicator in top-right corner
                if (isPolling) {
                    PollingIndicator(
                        lastUpdateTime = lastUpdateTime,
                        modifier = Modifier.align(Alignment.TopEnd)
                    )
                }
            }
        }
    }
}

@Composable
fun OutlinedSection(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp)
    ) {
        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        content()
    }
}

@Composable
fun LoadingView() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Loading game data...",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun NoGameView(nextGame: Game?, selectedTeam: NBATeam) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🏀",
            fontSize = 64.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No game today",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )
        nextGame?.let {
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedSection(modifier = Modifier.fillMaxWidth(0.9f)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🕐 ${selectedTeam.name} vs ${getOpponentName(it, selectedTeam.tricode)}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(32.dp)
                    ) {
                        Text(
                            text = getGameDateDisplay(it),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = GameRepository.formatGameTime(it.gameTimeUTC),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ScheduledGameView(game: Game, selectedTeam: NBATeam) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Game Details & Matchup Section
        OutlinedSection {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = getGameTitle(game, selectedTeam),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                game.arenaName?.let { arena ->
                    Text(
                        text = arena,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                // Show game date and time
                Text(
                    text = getGameDateDisplay(game),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = GameRepository.formatGameTime(game.gameTimeUTC),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Score placeholder
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isTeamHome = game.homeTeam.teamTricode == selectedTeam.tricode
                    val teamData = if (isTeamHome) game.homeTeam else game.awayTeam
                    val oppData = if (isTeamHome) game.awayTeam else game.homeTeam

                    TeamScore(selectedTeam.tricode, "--", "${teamData.wins}-${teamData.losses}")
                    Text("vs", style = MaterialTheme.typography.bodyLarge)
                    TeamScore(
                        getOpponentTricode(game, selectedTeam.tricode),
                        "--",
                        "${oppData.wins}-${oppData.losses}"
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Game hasn't started yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun LiveGameView(game: Game, wormData: List<`in`.anupcshan.gswtracker.data.model.WormPoint> = emptyList(), selectedTeam: NBATeam) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Game Header & Score Section
        OutlinedSection {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = getGameTitle(game, selectedTeam),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                game.arenaName?.let { arena ->
                    Text(
                        text = arena,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text(
                    text = game.gameStatusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Live badge
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "LIVE",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Current Score
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isTeamHome = game.homeTeam.teamTricode == selectedTeam.tricode
                    val teamData = if (isTeamHome) game.homeTeam else game.awayTeam
                    val oppData = if (isTeamHome) game.awayTeam else game.homeTeam

                    TeamScore(selectedTeam.tricode, teamData.score.toString(), "${teamData.wins}-${teamData.losses}")
                    Text("vs", style = MaterialTheme.typography.bodyLarge)
                    TeamScore(
                        getOpponentTricode(game, selectedTeam.tricode),
                        oppData.score.toString(),
                        "${oppData.wins}-${oppData.losses}"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Game Statistics Section
        OutlinedSection {
            QuarterBreakdown(game, selectedTeam)
            // Worm chart (if data available)
            if (wormData.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                WormChart(wormData = wormData, teamTricode = selectedTeam.tricode)
            }
        }
    }
}

@Composable
fun FinalGameView(game: Game, wormData: List<`in`.anupcshan.gswtracker.data.model.WormPoint> = emptyList(), nextGame: Game?, selectedTeam: NBATeam) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Game Header & Score Section
        OutlinedSection {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = getGameTitle(game, selectedTeam),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                game.arenaName?.let { arena ->
                    Text(
                        text = arena,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text(
                    text = if (game.period > 4) "Final ${getPeriodDisplay(game.period)}" else "Final",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Final Score
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isTeamHome = game.homeTeam.teamTricode == selectedTeam.tricode
                    val teamData = if (isTeamHome) game.homeTeam else game.awayTeam
                    val oppData = if (isTeamHome) game.awayTeam else game.homeTeam

                    TeamScore(selectedTeam.tricode, teamData.score.toString(), "${teamData.wins}-${teamData.losses}")
                    Text("vs", style = MaterialTheme.typography.bodyLarge)
                    TeamScore(
                        getOpponentTricode(game, selectedTeam.tricode),
                        oppData.score.toString(),
                        "${oppData.wins}-${oppData.losses}"
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Winner text
                val isTeamHome = game.homeTeam.teamTricode == selectedTeam.tricode
                val teamScore = if (isTeamHome) game.homeTeam.score else game.awayTeam.score
                val oppScore = if (isTeamHome) game.awayTeam.score else game.homeTeam.score
                val teamWon = teamScore > oppScore

                Text(
                    text = if (teamWon) "${selectedTeam.tricode} Win!" else "${selectedTeam.tricode} Loss",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (teamWon) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        }

        // Next Game Section
        nextGame?.let {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedSection {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🕐 ${selectedTeam.name} vs ${getOpponentName(it, selectedTeam.tricode)}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(32.dp)
                    ) {
                        Text(
                            text = getGameDateDisplay(it),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = GameRepository.formatGameTime(it.gameTimeUTC),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Game Statistics Section
        OutlinedSection {
            QuarterBreakdown(game, selectedTeam)
            Spacer(modifier = Modifier.height(16.dp))
            WormChart(wormData = wormData, teamTricode = selectedTeam.tricode)
        }
    }
}

@Composable
fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "⚠️",
            fontSize = 64.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Unable to load game data",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

@Composable
fun TeamScore(teamCode: String, score: String, record: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = teamCode,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = score,
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = record,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun QuarterBreakdown(game: Game, selectedTeam: NBATeam) {
    val isTeamHome = game.homeTeam.teamTricode == selectedTeam.tricode
    val teamPeriods = if (isTeamHome) game.homeTeam.periods else game.awayTeam.periods
    val oppPeriods = if (isTeamHome) game.awayTeam.periods else game.homeTeam.periods

    // Determine max periods (includes OT)
    val maxPeriod = maxOf(
        teamPeriods.maxOfOrNull { it.period } ?: 4,
        oppPeriods.maxOfOrNull { it.period } ?: 4,
        4 // Always show at least 4 quarters
    )

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Header row
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "",
                modifier = Modifier.weight(0.3f),
                style = MaterialTheme.typography.bodyMedium
            )
            for (i in 1..maxPeriod) {
                Text(
                    text = getPeriodLabel(i),
                    modifier = Modifier.weight(0.175f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Divider()
        Spacer(modifier = Modifier.height(8.dp))

        // Team row
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = selectedTeam.tricode,
                modifier = Modifier.weight(0.3f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            for (i in 1..maxPeriod) {
                val score = teamPeriods.find { it.period == i }?.score?.toString() ?: "--"
                Text(
                    text = score,
                    modifier = Modifier.weight(0.175f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Opponent row
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = getOpponentTricode(game, selectedTeam.tricode),
                modifier = Modifier.weight(0.3f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            for (i in 1..maxPeriod) {
                val score = oppPeriods.find { it.period == i }?.score?.toString() ?: "--"
                Text(
                    text = score,
                    modifier = Modifier.weight(0.175f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun getOpponentName(game: Game, teamTricode: String): String {
    return if (game.homeTeam.teamTricode == teamTricode) {
        game.awayTeam.teamName
    } else {
        game.homeTeam.teamName
    }
}

private fun getOpponentTricode(game: Game, teamTricode: String): String {
    return if (game.homeTeam.teamTricode == teamTricode) {
        game.awayTeam.teamTricode
    } else {
        game.homeTeam.teamTricode
    }
}

private fun getGameTitle(game: Game, selectedTeam: NBATeam): String {
    return if (game.homeTeam.teamTricode == selectedTeam.tricode) {
        "🏀 ${selectedTeam.name} vs ${getOpponentName(game, selectedTeam.tricode)}"
    } else {
        "🏀 ${selectedTeam.name} @ ${getOpponentName(game, selectedTeam.tricode)}"
    }
}

private fun getGameDateDisplay(game: Game): String {
    // Try to parse the gameCode to extract the date
    // gameCode format: "YYYYMMDD/TEAMTEAM" (e.g., "20251122/GSWPOR")
    return try {
        val datePart = game.gameCode.split("/").firstOrNull()
        if (datePart != null && datePart.length == 8) {
            val year = datePart.substring(0, 4).toInt()
            val month = datePart.substring(4, 6).toInt()
            val day = datePart.substring(6, 8).toInt()

            val date = java.time.LocalDate.of(year, month, day)
            val formatter = java.time.format.DateTimeFormatter.ofPattern("EEEE, MMM d")
            date.format(formatter)
        } else {
            ""
        }
    } catch (e: Exception) {
        ""
    }
}

private fun getPeriodDisplay(period: Int): String {
    return when {
        period <= 4 -> "Q$period"
        period == 5 -> "OT"
        else -> "${period - 4}OT"
    }
}

private fun getPeriodLabel(period: Int): String {
    return when {
        period <= 4 -> "Q$period"
        period == 5 -> "OT"
        else -> "${period - 4}OT"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamSelector(
    selectedTeam: NBATeam,
    onTeamSelected: (NBATeam) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedTeam.tricode,
            onValueChange = {},
            readOnly = true,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .menuAnchor()
                .width(110.dp),
            colors = OutlinedTextFieldDefaults.colors(),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            singleLine = true
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            NBATeams.ALL_TEAMS.forEach { team ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = team.tricode,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = team.fullName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        onTeamSelected(team)
                        expanded = false
                    },
                    leadingIcon = if (team.tricode == selectedTeam.tricode) {
                        { Text("✓", style = MaterialTheme.typography.bodyLarge) }
                    } else null
                )
            }
        }
    }
}

@Composable
fun PollingIndicator(
    lastUpdateTime: Long?,
    modifier: Modifier = Modifier
) {
    // Calculate alpha based on time since last update
    var currentAlpha by remember { mutableStateOf(1f) }

    LaunchedEffect(lastUpdateTime) {
        if (lastUpdateTime != null) {
            // Start at full brightness
            currentAlpha = 1f

            // Gradually fade over 15 seconds
            val fadeStart = lastUpdateTime
            while (true) {
                val elapsed = System.currentTimeMillis() - fadeStart
                val progress = (elapsed / 15_000f).coerceIn(0f, 1f)

                // Fade from 1.0 to 0.2 (never fully invisible)
                currentAlpha = 1f - (progress * 0.8f)

                delay(100) // Update every 100ms for smooth animation

                if (progress >= 1f) break
            }
        }
    }

    Box(
        modifier = modifier
            .padding(8.dp)
            .size(12.dp)
            .alpha(currentAlpha)
            .background(
                color = Color(0xFF4CAF50), // Green
                shape = CircleShape
            )
    )
}
