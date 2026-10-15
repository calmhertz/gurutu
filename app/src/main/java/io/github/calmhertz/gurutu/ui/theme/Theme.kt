package io.github.calmhertz.gurutu.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MovableContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val SeedColor = Color(0xff64ff00)

private val DarkSeedColorScheme = darkColorScheme(
    primary = SeedColor,
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