package com.otli.app.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val OtliOrange = Color(0xFFE8590C)
private val OtliOrangeDark = Color(0xFFFFB27A)

private val LightColors = lightColorScheme(
    primary = OtliOrange,
    onPrimary = Color.White,
    secondary = Color(0xFF2B6E5B),
)

private val DarkColors = darkColorScheme(
    primary = OtliOrangeDark,
    onPrimary = Color(0xFF3B1500),
    secondary = Color(0xFF8FD3BE),
)

@Composable
fun OtliTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
