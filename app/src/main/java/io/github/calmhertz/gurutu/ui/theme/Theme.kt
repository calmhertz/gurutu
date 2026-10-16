package io.github.calmhertz.gurutu.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkSeedColorScheme = darkColorScheme(
    primary = Color(0xFF5B9BD5),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF8A8F98),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFF0B0B0D),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF16161A),
    onSurface = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFFB3B6BD),
    outline = Color(0xFF3A3A40)
)

@Composable
fun GurutuTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkSeedColorScheme,
        typography = Typography,
        content = content
    )
}
