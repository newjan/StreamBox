package com.streambox.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Deep charcoal surfaces with one vivid accent.
val Accent = Color(0xFF7C4DFF)
val AccentDim = Color(0xFF5E35B1)
private val NearBlack = Color(0xFF0E0F13)
private val Charcoal = Color(0xFF16181D)
private val CharcoalHigh = Color(0xFF1E2128)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = AccentDim,
    onPrimaryContainer = Color.White,
    secondary = Accent,
    onSecondary = Color.White,
    background = NearBlack,
    onBackground = Color(0xFFE6E6EB),
    surface = Charcoal,
    onSurface = Color(0xFFE6E6EB),
    surfaceVariant = CharcoalHigh,
    onSurfaceVariant = Color(0xFF9BA0AB),
    outline = Color(0xFF3A3F4B),
)

private val LightColors = lightColorScheme(
    primary = AccentDim,
    onPrimary = Color.White,
    background = Color(0xFFF7F7FA),
    onBackground = Color(0xFF17181C),
    surface = Color.White,
    onSurface = Color(0xFF17181C),
    surfaceVariant = Color(0xFFECECF2),
    onSurfaceVariant = Color(0xFF5A5F6A),
    outline = Color(0xFFC6C9D2),
)

private val TvDarkColors = androidx.tv.material3.darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    background = NearBlack,
    onBackground = Color(0xFFE6E6EB),
    surface = Charcoal,
    onSurface = Color(0xFFE6E6EB),
    surfaceVariant = CharcoalHigh,
    onSurfaceVariant = Color(0xFF9BA0AB),
    border = Color(0xFF3A3F4B),
)

private val TvLightColors = androidx.tv.material3.lightColorScheme(
    primary = AccentDim,
    onPrimary = Color.White,
    background = Color(0xFFF7F7FA),
    onBackground = Color(0xFF17181C),
    surface = Color.White,
    onSurface = Color(0xFF17181C),
)

/**
 * Applies both Material3 and TV Material themes so phone and TV component
 * sets render consistently anywhere in the tree.
 */
@Composable
fun StreamBoxTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    androidx.tv.material3.MaterialTheme(
        colorScheme = if (darkTheme) TvDarkColors else TvLightColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            content = content,
        )
    }
}
