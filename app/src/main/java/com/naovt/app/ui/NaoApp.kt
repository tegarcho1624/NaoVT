package com.naovt.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.naovt.app.MainActivity
import com.naovt.app.alerts.AlertDisplay
import com.naovt.app.overlay.NaoOverlayService
import com.naovt.app.overlay.rememberOverlayController
import com.naovt.app.ui.alerts.AlertsScreen
import com.naovt.app.ui.phrases.PhrasesScreen
import com.naovt.app.ui.pomodoro.PomodoroScreen
import com.naovt.app.ui.voice.VoiceSettingsScreen
import com.naovt.app.ui.vault.VaultScreen

/**
 * Raíz de la app. El Baúl y Mis Alertas se dibujan encima de la pantalla principal, que se queda
 * viva por debajo (así conserva la habitación elegida al volver).
 *
 * [destination] permite abrir directo en una pantalla (lo usa el menú radial del Modo Flotante).
 * [alertDisplay] hace que Nao aparezca con la expresión de una alerta (al tocar su notificación).
 */
@Composable
fun NaoApp(
    destination: String? = null,
    onDestinationHandled: () -> Unit = {},
    alertDisplay: AlertDisplay? = null,
    onAlertDisplayDone: () -> Unit = {},
) {
    var vaultOpen by rememberSaveable { mutableStateOf(false) }
    var alertsOpen by rememberSaveable { mutableStateOf(false) }
    var phrasesOpen by rememberSaveable { mutableStateOf(false) }
    var pomodoroOpen by rememberSaveable { mutableStateOf(false) }
    var voiceSettingsOpen by rememberSaveable { mutableStateOf(false) }
    val overlay = rememberOverlayController()

    LaunchedEffect(destination) {
        when (destination) {
            MainActivity.DESTINATION_VAULT -> vaultOpen = true
            MainActivity.DESTINATION_ALERTS -> alertsOpen = true
            MainActivity.DESTINATION_POMODORO -> pomodoroOpen = true
        }
        if (destination != null) onDestinationHandled()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NaoScreen(
            onOpenVault = { vaultOpen = true },
            onOpenAlerts = { alertsOpen = true },
            onOpenPhrases = { phrasesOpen = true },
            onOpenPomodoro = { pomodoroOpen = true },
            onOpenVoiceSettings = { voiceSettingsOpen = true },
            isOverlayRunning = NaoOverlayService.isRunning,
            onToggleOverlay = { if (NaoOverlayService.isRunning) overlay.stop() else overlay.start() },
            alertDisplay = alertDisplay,
            onAlertDisplayDone = onAlertDisplayDone,
        )

        AnimatedVisibility(
            visible = vaultOpen,
            enter = fadeIn() + slideInVertically { it / 12 },
            exit = fadeOut(),
        ) {
            VaultScreen(onClose = { vaultOpen = false })
        }

        AnimatedVisibility(
            visible = alertsOpen,
            enter = fadeIn() + slideInVertically { it / 12 },
            exit = fadeOut(),
        ) {
            AlertsScreen(onClose = { alertsOpen = false })
        }

        AnimatedVisibility(
            visible = phrasesOpen,
            enter = fadeIn() + slideInVertically { it / 12 },
            exit = fadeOut(),
        ) {
            PhrasesScreen(onClose = { phrasesOpen = false })
        }

        AnimatedVisibility(
            visible = pomodoroOpen,
            enter = fadeIn() + slideInVertically { it / 12 },
            exit = fadeOut(),
        ) {
            PomodoroScreen(onClose = { pomodoroOpen = false })
        }

        AnimatedVisibility(
            visible = voiceSettingsOpen,
            enter = fadeIn() + slideInVertically { it / 12 },
            exit = fadeOut(),
        ) {
            VoiceSettingsScreen(onClose = { voiceSettingsOpen = false })
        }
    }
}
