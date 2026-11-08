package com.naovt.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.naovt.app.alerts.AlertDisplay
import com.naovt.app.ui.NaoApp
import com.naovt.app.ui.avatar.NaoExpression
import com.naovt.app.ui.theme.NaoVTTheme

class MainActivity : ComponentActivity() {

    // Pantalla a la que abrir directo (la pide el menú radial del Modo Flotante).
    private var destination by mutableStateOf<String?>(null)

    // Alerta que se acaba de tocar en la notificación: Nao la muestra con su expresión.
    private var alertDisplay by mutableStateOf<AlertDisplay?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Solo la primera vez: al rotar la pantalla no se debe volver a abrir ni a mostrar.
        if (savedInstanceState == null) readIntent(intent)
        setContent {
            NaoVTTheme {
                NaoApp(
                    destination = destination,
                    onDestinationHandled = { destination = null },
                    alertDisplay = alertDisplay,
                    onAlertDisplayDone = { alertDisplay = null },
                )
            }
        }
    }

    // La app ya estaba abierta y alguien (el overlay o una notificación) la abre de nuevo.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent(intent)
    }

    private fun readIntent(intent: Intent?) {
        destination = intent?.getStringExtra(EXTRA_DESTINATION)

        val text = intent?.getStringExtra(EXTRA_ALERT_TEXT)
        alertDisplay = if (text != null) {
            val expression = intent?.getStringExtra(EXTRA_ALERT_EXPRESSION)
                ?.let { name -> runCatching { NaoExpression.valueOf(name) }.getOrNull() }
                ?: NaoExpression.Neutral
            AlertDisplay(expression, text)
        } else {
            null
        }
    }

    companion object {
        const val EXTRA_DESTINATION = "com.naovt.app.extra.DESTINATION"
        const val DESTINATION_VAULT = "vault"
        const val DESTINATION_ALERTS = "alerts"
        const val DESTINATION_POMODORO = "pomodoro"

        const val EXTRA_ALERT_TEXT = "com.naovt.app.extra.ALERT_TEXT"
        const val EXTRA_ALERT_EXPRESSION = "com.naovt.app.extra.ALERT_EXPRESSION"
    }
}
