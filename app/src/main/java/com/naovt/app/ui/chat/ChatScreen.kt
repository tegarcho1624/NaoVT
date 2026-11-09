package com.naovt.app.ui.chat

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.naovt.app.R
import com.naovt.app.ui.avatar.NaoExpression
import com.naovt.app.ui.avatar.NaoHeadBadge
import com.naovt.app.ui.avatar.NaoMouth
import com.naovt.app.ui.avatar.rememberBlink
import com.naovt.app.ui.avatar.rememberTalkingMouth
import com.naovt.app.voice.rememberNaoVoice

/**
 * Chat con Nao. La respuesta aparece token por token y Nao la va diciendo oración por oración
 * con su voz, así empieza a hablar antes de que termine de pensar.
 */
@Composable
fun ChatScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = viewModel(),
) {
    val voice = rememberNaoVoice()
    val talkingMouth = rememberTalkingMouth(voice.isSpeaking, openMouth = NaoMouth.Happy)
    val blinking = rememberBlink(canBlink = !voice.isSpeaking)
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    var input by rememberSaveable { mutableStateOf("") }

    // Conecta la voz de esta pantalla con el chat.
    DisposableEffect(voice) {
        viewModel.speaker = object : ChatSpeaker {
            override fun speak(text: String, first: Boolean) {
                if (first) voice.speak(text) else voice.enqueue(text)
            }

            override fun stop() = voice.stop()
        }
        onDispose { viewModel.speaker = null }
    }

    LaunchedEffect(Unit) { viewModel.ensureModelLoaded() }

    // Los modelos .gguf no tienen tipo MIME conocido: se muestran todos los archivos.
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importModel(uri)
    }

    LaunchedEffect(viewModel.importError) {
        viewModel.importError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearImportError()
        }
    }

    // Se mantiene la vista al final mientras llega la respuesta.
    val messages = viewModel.messages
    val lastLength = messages.lastOrNull()?.text?.length ?: 0
    val totalItems = messages.size + if (viewModel.isGenerating) 1 else 0
    LaunchedEffect(totalItems, lastLength) {
        if (totalItems > 0) listState.scrollToItem(totalItems - 1)
    }

    val close = {
        viewModel.stopGeneration()
        onClose()
    }
    BackHandler(onBack = close)

    Surface(
        modifier = modifier
            .fillMaxSize()
            // Evita que los toques atraviesen y lleguen a la pantalla de abajo.
            .pointerInput(Unit) { detectTapGestures { } },
        color = MaterialTheme.colorScheme.background,
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing, // incluye el teclado
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                ChatHeader(
                    talkingMouth = talkingMouth,
                    blinking = blinking,
                    canClear = messages.isNotEmpty(),
                    onClear = viewModel::newConversation,
                    onClose = close,
                )

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (val state = viewModel.modelState) {
                        ChatModelState.Idle, ChatModelState.Loading ->
                            Busy(stringResource(R.string.chat_loading))

                        ChatModelState.Importing ->
                            Busy(stringResource(R.string.chat_importing))

                        ChatModelState.NotFound -> Message(
                            title = stringResource(R.string.chat_model_missing_title),
                            text = stringResource(R.string.chat_model_missing_text),
                        ) {
                            Button(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                                Text(stringResource(R.string.chat_import))
                            }
                        }

                        is ChatModelState.Failed -> Message(
                            title = stringResource(R.string.chat_load_failed_title),
                            text = state.message,
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(onClick = viewModel::retryLoad) {
                                    Text(stringResource(R.string.chat_retry))
                                }
                                OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                                    Text(stringResource(R.string.chat_import))
                                }
                            }
                        }

                        ChatModelState.Ready -> {
                            if (messages.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.chat_empty),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                                )
                            }
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(messages, key = { it.id }) { message ->
                                    // La burbuja de Nao aparece cuando llega su primer texto.
                                    if (message.fromUser || message.text.isNotEmpty()) ChatBubble(message)
                                }
                                if (viewModel.isGenerating) {
                                    item {
                                        Text(
                                            text = stringResource(R.string.chat_typing),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (viewModel.modelState == ChatModelState.Ready) {
                    InputBar(
                        value = input,
                        onValueChange = { input = it },
                        generating = viewModel.isGenerating,
                        onSend = {
                            viewModel.send(input)
                            input = ""
                        },
                        onStop = viewModel::stopGeneration,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatHeader(
    talkingMouth: NaoMouth?,
    blinking: Boolean,
    canClear: Boolean,
    onClear: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NaoHeadBadge(
            expression = NaoExpression.Neutral,
            size = 52.dp,
            mouthOverride = talkingMouth,
            isBlinking = blinking,
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = stringResource(R.string.chat_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.chat_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (canClear) {
            TextButton(onClick = onClear) { Text(stringResource(R.string.chat_new)) }
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.chat_close))
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val user = message.fromUser
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (user) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (user) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            modifier = Modifier.widthIn(max = 520.dp),
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (user) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    generating: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.chat_input_hint)) },
            maxLines = 4,
            enabled = !generating,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (value.isNotBlank()) onSend() }),
        )
        if (generating) {
            TextButton(onClick = onStop) { Text(stringResource(R.string.chat_stop)) }
        } else {
            IconButton(onClick = onSend, enabled = value.isNotBlank()) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.chat_send))
            }
        }
    }
}

@Composable
private fun Busy(text: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Message(title: String, text: String, actions: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        actions()
    }
}
