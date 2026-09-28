package me.legend.app.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Paper = Color(0xFFF5F0E6)
private val Ink = Color(0xFF292622)
private val MutedInk = Color(0xFF6B6258)
private val Accent = Color(0xFF7C4937)

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Color(0xFFFBF8F1),
    onSurface = Ink,
    onSurfaceVariant = MutedInk,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD7A58F),
    background = Color(0xFF1D1A18),
    surface = Color(0xFF28231F),
)

@Composable
fun LegendMeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
