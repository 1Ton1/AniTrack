package com.darren.anitrackpulse.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = AppPrimary,
    onPrimary = AppOnPrimary,
    background = AppBackgroundDark,
    surface = AppSurfaceDark,
    surfaceVariant = AppSurfaceVariantDark,
    onSurface = AppOnSurfaceDark,
    onSurfaceVariant = AppOnSurfaceMutedDark
)

private val LightColors = lightColorScheme(
    primary = AppPrimary,
    onPrimary = AppOnPrimary,
    background = AppBackgroundLight,
    surface = AppSurfaceLight,
    surfaceVariant = AppSurfaceVariantLight,
    onSurface = AppOnSurfaceLight,
    onSurfaceVariant = AppOnSurfaceMutedLight
)

@Composable
fun AniTrackPulseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
