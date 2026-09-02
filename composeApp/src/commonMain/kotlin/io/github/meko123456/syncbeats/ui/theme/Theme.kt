package io.github.meko123456.syncbeats.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF3949AB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = Color(0xFF00105C),
    secondary = Color(0xFF7B1FA2),
    onSecondary = Color.White,
    surface = Color(0xFFFBF8FF),
    background = Color(0xFFFBF8FF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9C3FF),
    onPrimary = Color(0xFF08218A),
    primaryContainer = Color(0xFF29399F),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFFE1B6F2),
    onSecondary = Color(0xFF4A0F63),
    surface = Color(0xFF121318),
    background = Color(0xFF121318),
)

@Composable
fun SyncBeatsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
