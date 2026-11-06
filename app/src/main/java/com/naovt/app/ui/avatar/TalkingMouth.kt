package com.naovt.app.ui.avatar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

private const val MOUTH_TOGGLE_MS = 140L

/**
 * Sincronización simple de la boca: mientras [isSpeaking] sea true alterna
 * entre boca cerrada y boca de beso. Devuelve null cuando no habla
 * (así el avatar usa la boca de su expresión normal).
 */
@Composable
fun rememberTalkingMouth(isSpeaking: Boolean): NaoMouth? {
    var open by remember { mutableStateOf(false) }

    LaunchedEffect(isSpeaking) {
        if (isSpeaking) {
            while (true) {
                open = !open
                delay(MOUTH_TOGGLE_MS)
            }
        } else {
            open = false
        }
    }

    return when {
        !isSpeaking -> null
        open -> NaoMouth.Open
        else -> NaoMouth.Closed
    }
}
