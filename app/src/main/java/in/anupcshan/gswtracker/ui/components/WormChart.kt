package `in`.anupcshan.gswtracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.anupcshan.gswtracker.data.model.WormPoint
import `in`.anupcshan.gswtracker.ui.theme.GswLosing
import `in`.anupcshan.gswtracker.ui.theme.GswWinning
import kotlin.math.abs
import kotlin.math.max

@Composable
fun WormChart(
    wormData: List<WormPoint>,
    modifier: Modifier = Modifier,
    teamTricode: String = "GSW"
) {
    val separatorColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    if (wormData.isEmpty()) {
        // Show placeholder when no data
        Box(
            modifier = modifier.height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Worm chart will appear after first quarter",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Column(modifier = modifier) {
        Text(
            text = "Score Differential",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Calculate chart bounds
        val maxScoreDiff = wormData.maxOfOrNull { abs(it.scoreDiff) } ?: 10
        val yAxisMax = max(10, ((maxScoreDiff + 5) / 10) * 10) // Round up to nearest 10

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            val width = size.width
            val height = size.height
            val padding = 40f // Padding for axes

            // Calculate scales
            val xScale = (width - padding * 2) / (wormData.last().gameTimeSeconds - wormData.first().gameTimeSeconds).coerceAtLeast(1)
            val yScale = (height - padding * 2) / (yAxisMax * 2)

            // Zero line (dashed)
            val zeroY = height / 2
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
            drawLine(
                color = Color.Gray,
                start = Offset(padding, zeroY),
                end = Offset(width - padding, zeroY),
                strokeWidth = 2f,
                pathEffect = dashEffect
            )

            // Quarter separator lines
            val quarterLength = 720 // 12 minutes per quarter in seconds
            val firstGameTime = wormData.first().gameTimeSeconds
            val lastGameTime = wormData.last().gameTimeSeconds
            val maxPeriodInData = wormData.maxOfOrNull { it.period } ?: 4

            // Draw lines at end of each quarter (Q1, Q2, Q3, ...)
            for (period in 1 until maxPeriodInData) {
                val quarterEndTime = period * quarterLength

                // Only draw if this time is within our data range
                if (quarterEndTime > firstGameTime && quarterEndTime < lastGameTime) {
                    val x = padding + (quarterEndTime - firstGameTime) * xScale
                    drawLine(
                        color = separatorColor,
                        start = Offset(x, padding),
                        end = Offset(x, height - padding),
                        strokeWidth = 2f
                    )
                }
            }

            // Draw the worm line with segments
            if (wormData.size > 1) {
                for (i in 0 until wormData.size - 1) {
                    val currentPoint = wormData[i]
                    val nextPoint = wormData[i + 1]

                    val x1 = padding + (currentPoint.gameTimeSeconds - wormData.first().gameTimeSeconds) * xScale
                    val y1 = zeroY - (currentPoint.scoreDiff * yScale)
                    val x2 = padding + (nextPoint.gameTimeSeconds - wormData.first().gameTimeSeconds) * xScale
                    val y2 = zeroY - (nextPoint.scoreDiff * yScale)

                    // Determine color based on score differential
                    val lineColor = when {
                        currentPoint.scoreDiff >= 0 && nextPoint.scoreDiff >= 0 -> GswWinning
                        currentPoint.scoreDiff <= 0 && nextPoint.scoreDiff <= 0 -> GswLosing
                        else -> {
                            // Crossing zero line - use average
                            if ((currentPoint.scoreDiff + nextPoint.scoreDiff) / 2 >= 0) GswWinning else GswLosing
                        }
                    }

                    drawLine(
                        color = lineColor,
                        start = Offset(x1, y1),
                        end = Offset(x2, y2),
                        strokeWidth = 4f,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
            }

            // Draw Y-axis labels
            val yLabels = listOf(yAxisMax, 0, -yAxisMax)
            // Note: We can't draw text directly in Canvas in Compose,
            // so we'll just draw the axis lines
        }

        // X-axis labels (Quarter markers)
        val maxPeriod = wormData.maxOfOrNull { it.period } ?: 4
        val numLabels = maxOf(4, maxPeriod)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 40.dp, end = 40.dp, top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            for (period in 1..numLabels) {
                Text(
                    text = when {
                        period <= 4 -> "Q$period"
                        period == 5 -> "OT"
                        else -> "${period - 4}OT"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Current score differential
        val currentDiff = wormData.lastOrNull()?.scoreDiff ?: 0
        val diffText = when {
            currentDiff > 0 -> "$teamTricode +$currentDiff"
            currentDiff < 0 -> "$teamTricode ${currentDiff}"
            else -> "Tied"
        }
        Text(
            text = diffText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = when {
                currentDiff > 0 -> GswWinning
                currentDiff < 0 -> GswLosing
                else -> MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 8.dp)
        )
    }
}
