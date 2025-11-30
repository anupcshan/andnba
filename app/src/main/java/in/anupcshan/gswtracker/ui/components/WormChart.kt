package `in`.anupcshan.gswtracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import `in`.anupcshan.gswtracker.data.model.WormPoint
import `in`.anupcshan.gswtracker.ui.theme.GswLosing
import `in`.anupcshan.gswtracker.ui.theme.GswWinning
import kotlin.math.abs
import kotlin.math.max

@Composable
fun WormChart(
    wormData: List<WormPoint>,
    modifier: Modifier = Modifier,
    teamTricode: String = "GSW",
    onTimeSelected: ((Int) -> Unit)? = null
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
        // Calculate chart bounds
        val maxScoreDiff = wormData.maxOfOrNull { abs(it.scoreDiff) } ?: 10
        val yAxisMax = maxScoreDiff + 1 // Add 1 point buffer to avoid drawing on edge
        val firstGameTime = wormData.first().gameTimeSeconds
        val lastGameTime = wormData.last().gameTimeSeconds
        val padding = 40f

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .pointerInput(wormData, onTimeSelected) {
                    if (onTimeSelected != null) {
                        detectTapGestures { offset ->
                            val xScale = (size.width - padding * 2) / (lastGameTime - firstGameTime).coerceAtLeast(1)
                            val tappedTime = firstGameTime + ((offset.x - padding) / xScale).toInt()
                            val clampedTime = tappedTime.coerceIn(firstGameTime, lastGameTime)
                            onTimeSelected(clampedTime)
                        }
                    }
                }
        ) {
            val width = size.width
            val height = size.height

            // Calculate scales
            val xScale = (width - padding * 2) / (lastGameTime - firstGameTime).coerceAtLeast(1)
            val yScale = (height - padding * 2) / (yAxisMax * 2)

            // Draw alternating background colors for quarters
            val quarterLength = 720 // 12 minutes per quarter in seconds
            val maxPeriodInData = wormData.maxOfOrNull { it.period } ?: 4

            for (period in 1..maxPeriodInData) {
                val quarterStartTime = (period - 1) * quarterLength
                val quarterEndTime = period * quarterLength

                // Only draw if this quarter overlaps with our data range
                if (quarterEndTime > firstGameTime && quarterStartTime < lastGameTime) {
                    val startX = padding + maxOf(0, quarterStartTime - firstGameTime) * xScale
                    val endX = padding + minOf(lastGameTime - firstGameTime, quarterEndTime - firstGameTime) * xScale

                    // Alternate between two subtle background colors
                    val backgroundColor = if (period % 2 == 1) {
                        separatorColor.copy(alpha = 0.05f)
                    } else {
                        separatorColor.copy(alpha = 0.15f)
                    }

                    drawRect(
                        color = backgroundColor,
                        topLeft = Offset(startX, 0f),
                        size = androidx.compose.ui.geometry.Size(
                            width = endX - startX,
                            height = height
                        )
                    )
                }
            }

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

        }
    }
}
