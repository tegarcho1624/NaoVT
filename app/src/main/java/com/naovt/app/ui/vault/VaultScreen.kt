package com.naovt.app.ui.vault

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.naovt.app.R
import com.naovt.app.dialog.NaoMemoryPhrases
import com.naovt.app.ui.avatar.NaoExpression
import com.naovt.app.ui.avatar.NaoHeadBadge
import com.naovt.app.ui.avatar.rememberTalkingMouth
import com.naovt.app.voice.rememberNaoVoice

/**
 * Pantalla del Baúl de los Recuerdos. Mientras está abierta, Nao muestra su cara de cariño
 * y, cuando se guarda un recuerdo, dice una frase especial.
 */
@Composable
fun VaultScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VaultViewModel = viewModel(),
) {
    val memories = viewModel.memories
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val selected = memories.firstOrNull { it.id == selectedId }

    val snackbarHostState = remember { SnackbarHostState() }
    val voice = rememberNaoVoice()
    val talkingMouth = rememberTalkingMouth(voice.isSpeaking)

    // Photo Picker nativo: no necesita permisos. Si el usuario cancela, uri llega null y no pasa nada.
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) pendingUri = uri
    }

    val saveFailedMessage = stringResource(R.string.vault_save_failed)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                VaultEvent.Saved -> voice.speak(NaoMemoryPhrases.newMemory())
                VaultEvent.SaveFailed -> snackbarHostState.showSnackbar(saveFailedMessage)
            }
        }
    }

    // Atrás: primero cierra el recuerdo abierto y luego el Baúl.
    BackHandler {
        if (selected != null) selectedId = null else onClose()
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            // Evita que los toques atraviesen el Baúl y lleguen a la pantalla de abajo.
            .pointerInput(Unit) { detectTapGestures { } },
        color = MaterialTheme.colorScheme.background,
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                if (selected == null) {
                    FloatingActionButton(
                        onClick = {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.vault_add))
                    }
                }
            },
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                if (selected != null) {
                    MemoryDetail(
                        memory = selected,
                        onClose = { selectedId = null },
                        onDelete = {
                            selectedId = null
                            viewModel.deleteMemory(selected)
                        },
                    )
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        VaultHeader(
                            memoryCount = memories.size,
                            talkingMouth = talkingMouth,
                            onClose = onClose,
                        )
                        MemoryGrid(
                            memories = memories,
                            onOpen = { selectedId = it.id },
                        )
                    }
                }
            }
        }
    }

    pendingUri?.let { uri ->
        AddMemoryDialog(
            imageUri = uri,
            onConfirm = { note ->
                viewModel.addMemory(uri, note)
                pendingUri = null
            },
            onDismiss = { pendingUri = null },
        )
    }
}

@Composable
private fun VaultHeader(
    memoryCount: Int,
    talkingMouth: com.naovt.app.ui.avatar.NaoMouth?,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Nao con ojos_corazon_mejilla y boca_besando mientras el Baúl está abierto.
        NaoHeadBadge(
            expression = NaoExpression.Loved,
            mouthOverride = talkingMouth,
            size = 72.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.vault_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = when (memoryCount) {
                    0 -> stringResource(R.string.vault_subtitle_empty)
                    1 -> stringResource(R.string.vault_count_one)
                    else -> stringResource(R.string.vault_count, memoryCount)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.vault_close_vault))
        }
    }
}
