package com.naovt.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.naovt.app.ui.vault.VaultScreen

/**
 * Raíz de la app. El Baúl se dibuja encima de la pantalla principal, que se queda viva por
 * debajo (así conserva la habitación elegida al volver).
 */
@Composable
fun NaoApp() {
    var vaultOpen by rememberSaveable { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        NaoScreen(onOpenVault = { vaultOpen = true })

        AnimatedVisibility(
            visible = vaultOpen,
            enter = fadeIn() + slideInVertically { it / 12 },
            exit = fadeOut(),
        ) {
            VaultScreen(onClose = { vaultOpen = false })
        }
    }
}
