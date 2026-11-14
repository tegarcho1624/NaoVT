package com.naovt.app.ui.avatar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.naovt.app.gestures.GesturesViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlin.random.Random

// El tiempo entre parpadeos varía al azar entre 2/3 y 4/3 del valor elegido en Gestos. Con el valor
// de siempre (4.5 s) eso es de 3 a 6 s.
private const val WAIT_MIN_FACTOR = 2f / 3f
private const val WAIT_MAX_FACTOR = 4f / 3f

/**
 * Parpadeo autónomo. Devuelve true mientras los ojos están cerrados. La frecuencia y la duración
 * salen de los ajustes de Gestos (por defecto: cada 3 a 6 s, durante 150 ms) y se leen en cada
 * ciclo, así que un cambio se nota en el siguiente parpadeo.
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
    val gestures: GesturesViewModel = viewModel()
    val currentSettings by rememberUpdatedState(gestures.settings)
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val settings = currentSettings
                val averageMs = settings.blinkIntervalSec * 1000f
                delay(Random.nextLong((averageMs * WAIT_MIN_FACTOR).toLong(), (averageMs * WAIT_MAX_FACTOR).toLong() + 1))
                if (currentCanBlink) {
                    eyesClosed = true
                    delay((currentSettings.blinkDurationSec * 1000f).toLong())
                    eyesClosed = false
                }
            }
        }
    }

    // Si en pleno parpadeo Nao empieza a hablar o a recibir cariño, el parpadeo se corta al instante.
    return eyesClosed && canBlink
}
