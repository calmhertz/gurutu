package io.github.calmhertz.gurutu.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkSeedColorScheme = darkColorScheme(
    primary = Color(0xffffffff),
    secondary = Color(0xff000000)
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