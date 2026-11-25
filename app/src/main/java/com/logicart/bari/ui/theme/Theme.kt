package com.logicart.bari.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = BariPine,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7EAE3),
    onPrimaryContainer = Color(0xFF123B33),
    secondary = BariAmber,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF6E2C7),
    onSecondaryContainer = Color(0xFF3A250C),
    background = BariCanvas,
    onBackground = BariInk,
    surface = BariSurface,
    onSurface = BariInk,
    surfaceVariant = Color(0xFFE9EEE9),
    onSurfaceVariant = BariMuted,
    outline = BariOutline,
    error = BariError,
)

private val DarkColors = darkColorScheme(
    primary = BariPineDark,
    onPrimary = Color(0xFF07372F),
    primaryContainer = Color(0xFF234F47),
    onPrimaryContainer = Color(0xFFD7EAE3),
    secondary = BariAmberDark,
    onSecondary = Color(0xFF432B0C),
    secondaryContainer = Color(0xFF5C411C),
    onSecondaryContainer = Color(0xFFF6E2C7),
    background = BariNight,
    onBackground = Color(0xFFE2E9E4),
    surface = BariNightSurface,
    onSurface = Color(0xFFE2E9E4),
    surfaceVariant = Color(0xFF303B36),
    onSurfaceVariant = Color(0xFFBBC7C0),
    outline = Color(0xFF84918A),
    error = Color(0xFFF2B8B5),
)

@Composable
fun BariTheme(
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = BariTypography,
        content = content,
    )
}

