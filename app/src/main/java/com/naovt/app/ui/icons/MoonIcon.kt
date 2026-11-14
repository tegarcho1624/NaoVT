package com.naovt.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Ícono de luna (el "bedtime" de Material). La app solo incluye `material-icons-core`, donde no viene
 * `Icons.Default.Bedtime`; por eso se dibuja a mano, igual que [TouchAppIcon].
 */
val MoonIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Moon",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = PathParser().parsePathString(MOON_PATH).toNodes(),
        fill = SolidColor(Color.Black),
    ).build()
}

private const val MOON_PATH =
    "M12 3c-4.97 0-9 4.03-9 9s4.03 9 9 9 9-4.03 9-9c0-.46-.04-.92-.1-1.36-.98 1.37-2.58 2.26-4.4 " +
        "2.26-2.98 0-5.4-2.42-5.4-5.4 0-1.81.89-3.42 2.26-4.4-.44-.06-.9-.1-1.36-.1z"
