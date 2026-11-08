package com.naovt.app.ui.voice

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.naovt.app.R
import com.naovt.app.ui.avatar.NaoExpression
import com.naovt.app.ui.avatar.NaoHeadBadge
import com.naovt.app.ui.avatar.NaoMouth
import com.naovt.app.ui.avatar.rememberBlink
import com.naovt.app.ui.avatar.rememberTalkingMouth
import com.naovt.app.voice.VoicePreferences
import com.naovt.app.voice.rememberNaoVoice
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Los botones "−" y "+" mueven los valores de 0.05 en 0.05.
private const val STEP = 0.05f

/**
 * Ajustes de Voz: volumen, tono y velocidad, con valor y botones − / +, "Probar Voz" y "Restablecer".
 * Cada cambio se guarda al instante; [com.naovt.app.voice.NaoVoice] los lee cada vez que habla,
 * así que "Probar Voz" ya suena con los valores nuevos.
 */
@Composable
fun VoiceSettingsScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val voice = rememberNaoVoice()
    val talkingMouth = rememberTalkingMouth(voice.isSpeaking, openMouth = NaoMouth.Happy)
    val blinking = rememberBlink(canBlink = !voice.isSpeaking)

    var enabled by remember { mutableStateOf(VoicePreferences.isEnabled(context)) }
    var volume by remember { mutableFloatStateOf(VoicePreferences.volume(context)) }
    var pitch by remember { mutableFloatStateOf(VoicePreferences.pitch(context)) }
    var rate by remember { mutableFloatStateOf(VoicePreferences.speechRate(context)) }

    // El interruptor de silencio también se cambia desde el menú radial: se relee al volver.
    LifecycleResumeEffect(Unit) {
        enabled = VoicePreferences.isEnabled(context)
        onPauseOrDispose { }
    }

    val mutedMessage = stringResource(R.string.voice_settings_muted)
    val testPhrase = stringResource(R.string.voice_settings_test_phrase)

    BackHandler(onBack = onClose)

    Surface(
        modifier = modifier
            .fillMaxSize()
            // Evita que los toques atraviesen y lleguen a la pantalla de abajo.
            .pointerInput(Unit) { detectTapGestures { } },
        color = MaterialTheme.colorScheme.background,
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.voice_settings_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.voice_settings_close))
                    }
                }

                Spacer(Modifier.height(8.dp))
                NaoHeadBadge(
                    expression = NaoExpression.Neutral,
                    size = 80.dp,
                    mouthOverride = talkingMouth,
                    isBlinking = blinking,
                )

                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.voice_settings_enabled),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = enabled,
                        onCheckedChange = {
                            enabled = it
                            VoicePreferences.setEnabled(context, it)
                        },
                    )
                }

                Spacer(Modifier.height(8.dp))
                VoiceSliderRow(
                    label = stringResource(R.string.voice_settings_volume),
                    value = volume,
                    min = VoicePreferences.MIN_VOLUME,
                    max = VoicePreferences.MAX_VOLUME,
                    onChange = {
                        volume = it
                        VoicePreferences.setVolume(context, it)
                    },
                )
                Text(
                    text = stringResource(R.string.voice_settings_volume_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))
                VoiceSliderRow(
                    label = stringResource(R.string.voice_settings_pitch),
                    value = pitch,
                    min = VoicePreferences.MIN_PITCH,
                    max = VoicePreferences.MAX_PITCH,
                    onChange = {
                        pitch = it
                        VoicePreferences.setPitch(context, it)
                    },
                )
                Spacer(Modifier.height(8.dp))
                VoiceSliderRow(
                    label = stringResource(R.string.voice_settings_rate),
                    value = rate,
                    min = VoicePreferences.MIN_SPEECH_RATE,
                    max = VoicePreferences.MAX_SPEECH_RATE,
                    onChange = {
                        rate = it
                        VoicePreferences.setSpeechRate(context, it)
                    },
                )

                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        if (VoicePreferences.isEnabled(context)) {
                            voice.speak(testPhrase)
                        } else {
                            scope.launch { snackbarHostState.showSnackbar(mutedMessage) }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.voice_settings_test))
                }

                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        VoicePreferences.resetToDefaults(context)
                        volume = VoicePreferences.DEFAULT_VOLUME
                        pitch = VoicePreferences.DEFAULT_PITCH
                        rate = VoicePreferences.DEFAULT_SPEECH_RATE
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.voice_settings_reset)) }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** Etiqueta con su valor, y debajo "−", el deslizador y "+". */
@Composable
private fun VoiceSliderRow(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    onChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "%.2f".format(value),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = { onChange(snap(value - STEP).coerceIn(min, max)) }) {
                Text("−", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            }
            Slider(
                value = value,
                onValueChange = { onChange(snap(it).coerceIn(min, max)) },
                valueRange = min..max,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onChange(snap(value + STEP).coerceIn(min, max)) }) {
                Text("+", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}

/** Redondea a múltiplos de 0.05 para que los valores salgan limpios (1.15, 0.95...). */
private fun snap(value: Float): Float = (value * 20f).roundToInt() / 20f
