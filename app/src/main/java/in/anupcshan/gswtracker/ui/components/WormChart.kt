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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
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

    // Find max lead and max trail points
    val maxLeadPoint = wormData.maxByOrNull { it.scoreDiff }
    val maxTrailPoint = wormData.minByOrNull { it.scoreDiff }

    Column(modifier = modifier) {
        // Calculate chart bounds
        val maxScoreDiff = wormData.maxOfOrNull { abs(it.scoreDiff) } ?: 10
        val yAxisMax = maxScoreDiff + 1 // Add 1 point buffer to avoid drawing on edge
        val firstGameTime = wormData.first().gameTimeSeconds
        val lastGameTime = wormData.last().gameTimeSeconds
        val rightPadding = 100f // Extra space for annotations
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
            val xScale = (width - padding - rightPadding) / (lastGameTime - firstGameTime).coerceAtLeast(1)
            val yScale = (height - padding * 2) / (yAxisMax * 2)

            // Draw alternating background colors for periods
            val maxPeriodInData = wormData.maxOfOrNull { it.period } ?: 4

            for (period in 1..maxPeriodInData) {
                val periodStartTime = getCumulativeTimeAtPeriodStart(period)
                val periodEndTime = periodStartTime + getPeriodLength(period)

                // Only draw if this period overlaps with our data range
                if (periodEndTime > firstGameTime && periodStartTime < lastGameTime) {
                    val startX = padding + maxOf(0, periodStartTime - firstGameTime) * xScale
                    val endX = padding + minOf(lastGameTime - firstGameTime, periodEndTime - firstGameTime) * xScale

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
            val chartRight = width - rightPadding
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
            drawLine(
                color = Color.Gray,
                start = Offset(padding, zeroY),
                end = Offset(chartRight, zeroY),
                strokeWidth = 2f,
                pathEffect = dashEffect
            )

            // Period separator lines
            // Draw lines at end of each period (Q1, Q2, Q3, Q4, OT1, ...)
            for (period in 1 until maxPeriodInData) {
                val periodEndTime = getCumulativeTimeAtPeriodStart(period) + getPeriodLength(period)

                // Only draw if this time is within our data range
                if (periodEndTime > firstGameTime && periodEndTime < lastGameTime) {
                    val x = padding + (periodEndTime - firstGameTime) * xScale
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

            // Draw max lead reference line and annotation
            maxLeadPoint?.let { point ->
                if (point.scoreDiff > 0) {
                    val y = zeroY - (point.scoreDiff * yScale)
                    // Dashed reference line
                    drawLine(
                        color = GswWinning.copy(alpha = 0.5f),
                        start = Offset(padding, y),
                        end = Offset(chartRight, y),
                        strokeWidth = 1f,
                        pathEffect = dashEffect
                    )
                    // Annotation
                    drawContext.canvas.nativeCanvas.drawText(
                        "+${point.scoreDiff} ${formatGameTime(point)}",
                        chartRight + 8f,
                        y + 12f,
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.rgb(76, 175, 80) // GswWinning
                            textSize = 28f
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        }
                    )
                }
            }

            // Draw max trail reference line and annotation
            maxTrailPoint?.let { point ->
                if (point.scoreDiff < 0) {
                    val y = zeroY - (point.scoreDiff * yScale)
                    // Dashed reference line
                    drawLine(
                        color = GswLosing.copy(alpha = 0.5f),
                        start = Offset(padding, y),
                        end = Offset(chartRight, y),
                        strokeWidth = 1f,
                        pathEffect = dashEffect
                    )
                    // Annotation
                    drawContext.canvas.nativeCanvas.drawText(
                        "${point.scoreDiff} ${formatGameTime(point)}",
                        chartRight + 8f,
                        y + 12f,
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.rgb(244, 67, 54) // GswLosing
                            textSize = 28f
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        }
                    )
                }
            }
        }
    }
}

private const val REGULATION_QUARTER_LENGTH = 720 // 12 minutes in seconds
private const val OVERTIME_LENGTH = 300 // 5 minutes in seconds

/**
 * Get the length of a period in seconds
 */
private fun getPeriodLength(period: Int): Int =
    if (period <= 4) REGULATION_QUARTER_LENGTH else OVERTIME_LENGTH

/**
 * Get the cumulative game time at the start of a period
 */
private fun getCumulativeTimeAtPeriodStart(period: Int): Int {
    val completedRegulationQuarters = minOf(period - 1, 4)
    val completedOvertimes = maxOf(0, period - 5)
    return (completedRegulationQuarters * REGULATION_QUARTER_LENGTH) +
            (completedOvertimes * OVERTIME_LENGTH)
}

/**
 * Format game time from WormPoint to readable string like "Q3 4:32" or "OT 2:30"
 */
private fun formatGameTime(point: WormPoint): String {
    val periodLength = getPeriodLength(point.period)
    val periodStartTime = getCumulativeTimeAtPeriodStart(point.period)
    val timeInPeriod = point.gameTimeSeconds - periodStartTime
    val timeRemaining = periodLength - timeInPeriod
    val minutes = timeRemaining / 60
    val seconds = timeRemaining % 60
    val periodLabel = if (point.period <= 4) "Q${point.period}" else "OT${point.period - 4}"
    return "$periodLabel $minutes:${seconds.toString().padStart(2, '0')}"
}
