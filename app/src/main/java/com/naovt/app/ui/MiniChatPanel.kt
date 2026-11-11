package com.naovt.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.naovt.app.R
import kotlinx.coroutines.delay

/**
 * Mini chat de voz de la pantalla principal: solo un campo de texto y un botón de enviar.
 * La respuesta de Nao no se escribe aquí: se dice en voz alta. Mientras piensa se ven tres puntitos.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MiniChatPanel(
    value: String,
    onValueChange: (String) -> Unit,
    thinking: Boolean,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // Al abrir el panel, el cursor queda en el campo y sale el teclado.
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
        keyboard?.show()
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 4.dp,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    placeholder = { Text(stringResource(R.string.mini_chat_hint)) },
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (value.isNotBlank() && !thinking) onSend() }),
                )
                IconButton(onClick = onSend, enabled = value.isNotBlank() && !thinking) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.mini_chat_send))
                }
            }
            if (thinking) ThinkingDots()
        }
    }
}

/** Tres puntitos que se van llenando: ".", "..", "..." */
@Composable
private fun ThinkingDots() {
    var count by remember { mutableIntStateOf(1) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(400)
            count = count % 3 + 1
        }
    }
    Text(
        text = ".".repeat(count),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 12.dp),
    )
}
