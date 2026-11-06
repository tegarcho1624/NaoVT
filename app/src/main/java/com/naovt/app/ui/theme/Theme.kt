package com.naovt.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NaoDarkColors = darkColorScheme(
    primary = Color(0xFFFF8FB8),
    onPrimary = Color(0xFF3A0A1F),
    primaryContainer = Color(0xFF5A1F3A),
    onPrimaryContainer = Color(0xFFFFD9E6),
    background = Color(0xFF121016),
    onBackground = Color(0xFFEDE0E6),
    surface = Color(0xFF1B1820),
    onSurface = Color(0xFFEDE0E6),
)

/** Tema oscuro fijo: resalta al personaje sin importar el modo del sistema. */
@Composable
fun NaoVTTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NaoDarkColors, content = content)
}
