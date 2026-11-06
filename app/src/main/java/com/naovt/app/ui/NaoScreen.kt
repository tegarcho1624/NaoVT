package com.naovt.app.ui

import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.naovt.app.R
import com.naovt.app.ui.avatar.NaoExpression
import com.naovt.app.ui.avatar.NaoMouth
import com.naovt.app.dialog.NaoPhrases
import com.naovt.app.ui.avatar.NaoVTAvatar
import com.naovt.app.ui.avatar.rememberTalkingMouth
import com.naovt.app.ui.room.Room
import com.naovt.app.ui.room.RoomBackground
import com.naovt.app.voice.NaoVoice
import com.naovt.app.voice.rememberNaoVoice
import kotlinx.coroutines.delay

private const val LOVED_DURATION_MS = 2000L

/** Pantalla principal: fondo + avatar + botón para cambiar de habitación. */
@Composable
fun NaoScreen(
    onOpenVault: () -> Unit = {},
    isOverlayRunning: Boolean = false,
    onToggleOverlay: () -> Unit = {},
) {
    var room by rememberSaveable { mutableStateOf(Room.NaoRoom) }
    var tapCount by remember { mutableIntStateOf(0) }
    var expression by remember { mutableStateOf(NaoExpression.Neutral) }
    var lastPhrase by remember { mutableStateOf<String?>(null) }
    var pendingPhrase by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val voice = rememberNaoVoice()
    val talkingMouth = rememberTalkingMouth(voice.isSpeaking, openMouth = NaoMouth.Happy)

    // Avisa si el teléfono no puede hablar (sin voz en español o sin motor TTS).
    LaunchedEffect(voice.problem) {
        val message = when (voice.problem) {
            NaoVoice.Problem.SpanishUnavailable -> R.string.voice_no_spanish
            NaoVoice.Problem.EngineFailed -> R.string.voice_engine_failed
            null -> return@LaunchedEffect
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    // Cariño: cara de beso 2 s, vuelve a la normalidad y entonces Nao dice su frase.
    // Cada toque reinicia el efecto. Hablar y dar cariño nunca se muestran a la vez.
    LaunchedEffect(tapCount) {
        if (tapCount > 0) {
            expression = NaoExpression.Loved
            delay(LOVED_DURATION_MS)
            expression = NaoExpression.Neutral
            pendingPhrase?.let { voice.speak(it) }
            pendingPhrase = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        RoomBackground(room = room)

        NaoVTAvatar(
            expression = expression,
            mouthOverride = talkingMouth,
            isSpeaking = voice.isSpeaking,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxHeight(0.85f)
                .pointerInput(Unit) {
                    detectTapGestures {
                        val phrase = NaoPhrases.random(previous = lastPhrase)
                        lastPhrase = phrase
                        if (voice.isSpeaking) {
                            // Ya está hablando: corta la frase anterior y sigue con la nueva (sin cariño).
                            voice.speak(phrase)
                        } else {
                            pendingPhrase = phrase
                            tapCount++ // cariño primero; la frase se dice al terminar
                        }
                    }
                },
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .safeDrawingPadding()
                .padding(12.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ExtendedFloatingActionButton(onClick = { room = room.next() }) {
                Text(stringResource(R.string.change_room))
            }
            ExtendedFloatingActionButton(
                onClick = onOpenVault,
                icon = { Icon(Icons.Default.Favorite, contentDescription = null) },
                text = { Text(stringResource(R.string.vault_button)) },
            )
            ExtendedFloatingActionButton(
                onClick = onToggleOverlay,
                icon = {
                    Icon(
                        if (isOverlayRunning) Icons.Default.Close else Icons.Default.Face,
                        contentDescription = null,
                    )
                },
                text = {
                    Text(
                        stringResource(
                            if (isOverlayRunning) R.string.overlay_stop_button else R.string.overlay_button
                        )
                    )
                },
            )
        }
    }
}
