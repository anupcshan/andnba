package com.gswtracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gswtracker.data.model.Game
import com.gswtracker.data.model.GameState
import com.gswtracker.ui.components.WormChart
import com.gswtracker.ui.viewmodel.GameViewModel
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(viewModel: GameViewModel) {
    val gameState by viewModel.gameState.collectAsState()
    val isRefreshing = gameState is GameState.Loading

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
                is GameState.NoGameToday -> NoGameView(state.nextGame)
                is GameState.GameScheduled -> ScheduledGameView(state.game)
                is GameState.GameLive -> LiveGameView(state.game, state.wormData)
                is GameState.GameFinal -> FinalGameView(state.game, state.wormData)
                is GameState.Error -> ErrorView(state.message) { viewModel.refreshGame() }
            }
        }
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
fun NoGameView(nextGame: Game?) {
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
            Text(
                text = "Next game:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Warriors vs ${getOpponentName(it)}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun ScheduledGameView(game: Game) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Text(
            text = getGameTitle(game),
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

        Spacer(modifier = Modifier.height(32.dp))

        // Score placeholder
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TeamScore("GSW", "--", "${game.homeTeam.wins}-${game.homeTeam.losses}")
            Text("vs", style = MaterialTheme.typography.bodyLarge)
            TeamScore(
                getOpponentTricode(game),
                "--",
                "${game.awayTeam.wins}-${game.awayTeam.losses}"
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Game hasn't started yet",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun LiveGameView(game: Game, wormData: List<com.gswtracker.data.model.WormPoint> = emptyList()) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Text(
            text = getGameTitle(game),
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
            text = "Q${game.period} ${game.gameClock}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Score
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isGswHome = game.homeTeam.teamTricode == "GSW"
            val gswTeam = if (isGswHome) game.homeTeam else game.awayTeam
            val oppTeam = if (isGswHome) game.awayTeam else game.homeTeam

            TeamScore("GSW", gswTeam.score.toString(), "${gswTeam.wins}-${gswTeam.losses}")
            Text("vs", style = MaterialTheme.typography.bodyLarge)
            TeamScore(
                getOpponentTricode(game),
                oppTeam.score.toString(),
                "${oppTeam.wins}-${oppTeam.losses}"
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

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

        Spacer(modifier = Modifier.height(32.dp))

        // Quarter scores
        QuarterBreakdown(game)

        // Worm chart (if data available)
        if (wormData.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
            WormChart(wormData = wormData)
        }
    }
}

@Composable
fun FinalGameView(game: Game, wormData: List<com.gswtracker.data.model.WormPoint> = emptyList()) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Text(
            text = getGameTitle(game),
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
            text = "Final",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Final Score
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isGswHome = game.homeTeam.teamTricode == "GSW"
            val gswTeam = if (isGswHome) game.homeTeam else game.awayTeam
            val oppTeam = if (isGswHome) game.awayTeam else game.homeTeam

            TeamScore("GSW", gswTeam.score.toString(), "${gswTeam.wins}-${gswTeam.losses}")
            Text("vs", style = MaterialTheme.typography.bodyLarge)
            TeamScore(
                getOpponentTricode(game),
                oppTeam.score.toString(),
                "${oppTeam.wins}-${oppTeam.losses}"
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Winner text
        val isGswHome = game.homeTeam.teamTricode == "GSW"
        val gswScore = if (isGswHome) game.homeTeam.score else game.awayTeam.score
        val oppScore = if (isGswHome) game.awayTeam.score else game.homeTeam.score
        val gswWon = gswScore > oppScore

        Text(
            text = if (gswWon) "GSW Win!" else "GSW Loss",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = if (gswWon) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Quarter scores
        QuarterBreakdown(game)

        // Worm chart
        Spacer(modifier = Modifier.height(24.dp))
        WormChart(wormData = wormData)
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
fun QuarterBreakdown(game: Game) {
    val isGswHome = game.homeTeam.teamTricode == "GSW"
    val gswPeriods = if (isGswHome) game.homeTeam.periods else game.awayTeam.periods
    val oppPeriods = if (isGswHome) game.awayTeam.periods else game.homeTeam.periods

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(8.dp)
            )
            .padding(16.dp)
    ) {
        // Header row
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "",
                modifier = Modifier.weight(0.3f),
                style = MaterialTheme.typography.bodyMedium
            )
            for (i in 1..4) {
                Text(
                    text = "Q$i",
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

        // GSW row
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "GSW",
                modifier = Modifier.weight(0.3f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            for (i in 1..4) {
                val score = gswPeriods.find { it.period == i }?.score?.toString() ?: "--"
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
                text = getOpponentTricode(game),
                modifier = Modifier.weight(0.3f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            for (i in 1..4) {
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

private fun getOpponentName(game: Game): String {
    return if (game.homeTeam.teamTricode == "GSW") {
        game.awayTeam.teamName
    } else {
        game.homeTeam.teamName
    }
}

private fun getOpponentTricode(game: Game): String {
    return if (game.homeTeam.teamTricode == "GSW") {
        game.awayTeam.teamTricode
    } else {
        game.homeTeam.teamTricode
    }
}

private fun getGameTitle(game: Game): String {
    return if (game.homeTeam.teamTricode == "GSW") {
        "🏀 Warriors vs ${getOpponentName(game)}"
    } else {
        "🏀 Warriors @ ${getOpponentName(game)}"
    }
}
