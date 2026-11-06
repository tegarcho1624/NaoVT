package com.naovt.app.ui.avatar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlin.random.Random

private const val MIN_WAIT_MS = 3000L
private const val MAX_WAIT_MS = 6000L
private const val BLINK_DURATION_MS = 150L

/**
 * Parpadeo autónomo. Cada 3 a 6 segundos (al azar) devuelve true durante 150 ms.
 *
 * Solo parpadea si [canBlink] es true en ese momento (Nao en estado base: sin hablar ni cariño).
 *
 * Ahorro de batería: el bucle vive solo mientras la pantalla está visible (STARTED).
 * Si la app pasa a segundo plano el bucle se cancela, y al volver empieza de nuevo.
 * También se cancela si el componente sale de la composición.
 */
@Composable
fun rememberBlink(canBlink: Boolean): Boolean {
    var eyesClosed by remember { mutableStateOf(false) }
    val currentCanBlink by rememberUpdatedState(canBlink)
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(Random.nextLong(MIN_WAIT_MS, MAX_WAIT_MS))
                if (currentCanBlink) {
                    eyesClosed = true
                    delay(BLINK_DURATION_MS)
                    eyesClosed = false
                }
            }
        }
    }

    // Si en pleno parpadeo Nao empieza a hablar o a recibir cariño, el parpadeo se corta al instante.
    return eyesClosed && canBlink
}
