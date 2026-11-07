package com.aura.assistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = AuraCyan,
    onPrimary = AuraDarkBackground,
    primaryContainer = AuraViolet,
    onPrimaryContainer = AuraTextPrimary,
    secondary = AuraMagenta,
    onSecondary = AuraTextPrimary,
    background = AuraDarkBackground,
    onBackground = AuraTextPrimary,
    surface = AuraDarkSurface,
    onSurface = AuraTextPrimary,
    surfaceVariant = AuraCardSurface,
    onSurfaceVariant = AuraTextSecondary
)

@Composable
fun AuraTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
