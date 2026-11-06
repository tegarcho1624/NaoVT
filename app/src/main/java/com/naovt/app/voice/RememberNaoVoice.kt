package com.naovt.app.voice

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Crea la [NaoVoice] ligada al ciclo de vida de la pantalla y la libera al salir. */
@Composable
fun rememberNaoVoice(): NaoVoice {
    val context = LocalContext.current
    val voice = remember { NaoVoice(context) }
    DisposableEffect(voice) {
        onDispose { voice.shutdown() }
    }
    return voice
}
