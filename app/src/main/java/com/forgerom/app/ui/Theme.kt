package com.forgerom.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val ForgeBlue = Color(0xFF0066FF)

private val LightColors = lightColorScheme(
    primary = ForgeBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF001A41)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4D94FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF00409E),
    onPrimaryContainer = Color(0xFFD6E4FF)
)

@Composable
fun ForgeRomTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
