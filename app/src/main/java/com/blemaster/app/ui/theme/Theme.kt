package com.blemaster.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Black,
    primaryContainer = SurfaceElevated,
    onPrimaryContainer = White,
    secondary = AccentSecondary,
    onSecondary = Black,
    secondaryContainer = SurfaceElevated,
    onSecondaryContainer = White,
    tertiary = Accent,
    onTertiary = Black,
    background = Black,
    onBackground = White,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    error = Error,
    onError = Black,
    errorContainer = Error,
    onErrorContainer = Black,
    outline = OnSurfaceDim,
    outlineVariant = SurfaceElevated
)

@Composable
fun BLEMasterTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
