package com.naovt.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.naovt.app.ui.NaoApp
import com.naovt.app.ui.theme.NaoVTTheme

class MainActivity : ComponentActivity() {

    // Pantalla a la que abrir directo (la pide el menú radial del Modo Flotante).
    private var destination by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Solo la primera vez: al rotar la pantalla no se debe volver a abrir.
        if (savedInstanceState == null) destination = intent?.getStringExtra(EXTRA_DESTINATION)
        setContent {
            NaoVTTheme {
                NaoApp(
                    destination = destination,
                    onDestinationHandled = { destination = null },
                )
            }
        }
    }

    // La app ya estaba abierta y alguien (el overlay) la abre de nuevo pidiendo una pantalla.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        destination = intent.getStringExtra(EXTRA_DESTINATION)
    }

    companion object {
        const val EXTRA_DESTINATION = "com.naovt.app.extra.DESTINATION"
        const val DESTINATION_VAULT = "vault"
        const val DESTINATION_ALERTS = "alerts"
    }
}
