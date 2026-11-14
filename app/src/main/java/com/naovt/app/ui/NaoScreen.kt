package com.naovt.app.ui

import android.widget.Toast
import androidx.lifecycle.viewmodel.compose.viewModel
import com.naovt.app.ui.rooms.RoomsViewModel
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import com.naovt.app.llm.SpokenAnswerer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.naovt.app.alerts.AlertDisplay
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
import com.naovt.app.data.PhraseRepository
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import com.naovt.app.ui.avatar.NaoLayeredAvatar
import com.naovt.app.gestures.rememberEffectiveGestures
import com.naovt.app.wardrobe.WardrobeViewModel
import com.naovt.app.ui.avatar.rememberBlink
import com.naovt.app.ui.avatar.rememberTalkingMouth
import com.naovt.app.ui.room.RoomBackground
import com.naovt.app.voice.NaoVoice
import com.naovt.app.voice.rememberNaoVoice
import kotlinx.coroutines.delay

private const val LOVED_DURATION_MS = 2000L
private const val ALERT_DISPLAY_MS = 8000L

// Mini chat de voz (toque largo sobre Nao).
private val MINI_CHAT_WIDTH = 300.dp
private val MINI_CHAT_HEIGHT_GUESS = 96.dp

/** Pantalla principal: fondo + avatar + botón para cambiar de habitación. */
@Composable
fun NaoScreen(
    onOpenVault: () -> Unit = {},
    onOpenAlerts: () -> Unit = {},
    onOpenPhrases: () -> Unit = {},
    onOpenPomodoro: () -> Unit = {},
    onOpenVoiceSettings: () -> Unit = {},
    onOpenChat: () -> Unit = {},
    onOpenRooms: () -> Unit = {},
    onOpenWardrobe: () -> Unit = {},
    onOpenGestures: () -> Unit = {},
    backgroundPaused: Boolean = false,
    isOverlayRunning: Boolean = false,
    onToggleOverlay: () -> Unit = {},
    alertDisplay: AlertDisplay? = null,
    onAlertDisplayDone: () -> Unit = {},
) {
    // Habitación activa (predeterminada o propia, imagen o video).
    val roomsViewModel: RoomsViewModel = viewModel()
    val activeRoom = roomsViewModel.activa
    // Atuendo de Nao (compartido con el Armario: no se pierde al cambiar de pantalla).
    val wardrobeViewModel: WardrobeViewModel = viewModel()
    // Ojos, boca y tiempos de los gestos: los del outfit puesto o, si no tiene, los globales.
    val gestures = rememberEffectiveGestures()
    var tapCount by remember { mutableIntStateOf(0) }
    var expression by remember { mutableStateOf(NaoExpression.Neutral) }
    var lastPhrase by remember { mutableStateOf<String?>(null) }
    var pendingPhrase by remember { mutableStateOf<String?>(null) }
    // Si se abrió desde una alerta, Nao muestra esa expresión un rato.
    val shownExpression = alertDisplay?.expression ?: expression

    val context = LocalContext.current
    val phraseRepository = remember { PhraseRepository(context) }
    val voice = rememberNaoVoice()
    // Parpadea solo en estado base: sin hablar y sin cara de cariño.
    val isBlinking = rememberBlink(
        canBlink = !voice.isSpeaking && shownExpression == NaoExpression.Neutral,
    )
    val talkingMouth = rememberTalkingMouth(voice.isSpeaking, openMouth = NaoMouth.Happy)

    // Mini chat de voz: escribes y Nao responde solo hablando (su texto no se muestra).
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    var miniChatOpen by remember { mutableStateOf(false) }
    var miniChatText by remember { mutableStateOf("") }
    var miniChatThinking by remember { mutableStateOf(false) }
    val answerer = remember {
        SpokenAnswerer(
            context = context,
            speaker = { text, first -> if (first) voice.speak(text) else voice.enqueue(text) },
            onThinkingChanged = { miniChatThinking = it },
            onProblem = { message -> Toast.makeText(context, message, Toast.LENGTH_LONG).show() },
        )
    }
    DisposableEffect(answerer) { onDispose { answerer.cancel() } }

    // Menú lateral (reemplaza a los botones flotantes de antes).
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

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

    // La expresión de la alerta se muestra unos segundos y luego Nao vuelve a su estado normal.
    LaunchedEffect(alertDisplay) {
        if (alertDisplay != null) {
            delay(ALERT_DISPLAY_MS)
            onAlertDisplayDone()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            NaoDrawerContent(
                isOverlayRunning = isOverlayRunning,
                onChat = onOpenChat,
                onAlerts = onOpenAlerts,
                onPhrases = onOpenPhrases,
                onPomodoro = onOpenPomodoro,
                onVoiceSettings = onOpenVoiceSettings,
                onGestures = onOpenGestures,
                onChangeRoom = onOpenRooms,
                onVault = onOpenVault,
                onWardrobe = onOpenWardrobe,
                onToggleOverlay = onToggleOverlay,
                afterClick = { scope.launch { drawerState.close() } }, // se cierra solo al elegir
            )
        },
    ) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        RoomBackground(habitacion = activeRoom, paused = backgroundPaused)

        NaoLayeredAvatar(
            config = wardrobeViewModel.config,
            gestures = gestures,
            expression = shownExpression,
            mouthOverride = talkingMouth,
            isSpeaking = voice.isSpeaking,
            isBlinking = isBlinking,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxHeight(0.85f)
                .pointerInput(Unit) {
                    detectTapGestures(
                        // Toque largo: abre o cierra el mini chat de voz.
                        onLongPress = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            miniChatOpen = !miniChatOpen
                        },
                        onTap = {
                        val phrase = phraseRepository.random(previous = lastPhrase)
                        lastPhrase = phrase
                        if (voice.isSpeaking) {
                            // Ya está hablando: corta la frase anterior y sigue con la nueva (sin cariño).
                            phrase?.let { voice.speak(it) }
                        } else {
                            pendingPhrase = phrase
                            tapCount++ // cariño primero; la frase se dice al terminar
                        }
                        },
                    )
                },
        )

        // Panel del mini chat, al lado de Nao (o sobre ella si la pantalla es angosta).
        if (miniChatOpen) {
            val naoHeight = maxHeight * 0.85f
            val naoWidth = minOf(naoHeight * (1024f / 1536f), maxWidth)
            val panelWidth = minOf(MINI_CHAT_WIDTH, maxWidth - 32.dp)
            val sideFits = (maxWidth - naoWidth) / 2 >= panelWidth + 16.dp
            val panelX: Dp = if (sideFits) (maxWidth + naoWidth) / 2 + 8.dp else (maxWidth - panelWidth) / 2
            val desiredY: Dp = if (sideFits) maxHeight * 0.30f else maxHeight - 168.dp
            // Que el teclado nunca tape el panel.
            val imeBottom = with(density) { WindowInsets.ime.getBottom(density).toDp() }
            val panelY = minOf(desiredY, maxHeight - imeBottom - MINI_CHAT_HEIGHT_GUESS - 16.dp)
                .coerceAtLeast(0.dp)

            MiniChatPanel(
                value = miniChatText,
                onValueChange = { miniChatText = it },
                thinking = miniChatThinking,
                onSend = { if (answerer.ask(miniChatText)) miniChatText = "" },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = panelX, y = panelY)
                    .width(panelWidth),
            )
        }

        // Botón del menú lateral (esquina superior izquierda).
        IconButton(
            onClick = { scope.launch { drawerState.open() } },
            modifier = Modifier
                .align(Alignment.TopStart)
                .safeDrawingPadding()
                .padding(12.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape),
        ) {
            Icon(
                Icons.Default.Menu,
                contentDescription = stringResource(R.string.drawer_open),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }

        // "Hablar" rápido: abre o cierra el mini chat de voz (lo mismo que mantener presionada a Nao).
        FloatingActionButton(
            onClick = { miniChatOpen = !miniChatOpen },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .safeDrawingPadding()
                .padding(16.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.quick_talk))
        }

        // Texto de la alerta que abrió la app; tocarlo lo cierra.
        if (alertDisplay != null) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .safeDrawingPadding()
                    .padding(24.dp)
                    .clickable { onAlertDisplayDone() },
            ) {
                Text(
                    text = alertDisplay.text,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                )
            }
        }
    }
    } // fin del menú lateral
}
