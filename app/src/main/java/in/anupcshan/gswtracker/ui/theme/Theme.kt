package `in`.anupcshan.gswtracker.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Warriors brand colors
val WarriorsGold = Color(0xFFFFC72C)
val WarriorsBlue = Color(0xFF1D428A)
val GswWinning = Color(0xFF4CAF50)
val GswLosing = Color(0xFFF44336)

private val DarkColorScheme = darkColorScheme(
    primary = WarriorsGold,
    secondary = WarriorsBlue,
    tertiary = GswWinning
)

private val LightColorScheme = lightColorScheme(
    primary = WarriorsGold,
    secondary = WarriorsBlue,
    tertiary = GswWinning
)

@Composable
fun GswTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
