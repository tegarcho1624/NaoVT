package com.naovt.app.ui.rooms

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.naovt.app.R
import com.naovt.app.data.Habitacion
import com.naovt.app.data.TipoHabitacion
import java.io.File

private const val MAX_NAME_LENGTH = 40

/**
 * Gestionar Habitaciones: "Oficiales" (sin eliminar) y "Mis Habitaciones" (con eliminar), y un botón "+"
 * para añadir una imagen o un video de la galería. Tocar una habitación la activa.
 */
@Composable
fun RoomsScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RoomsViewModel = viewModel(),
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showTypeDialog by rememberSaveable { mutableStateOf(false) }
    var pickingVideo by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Habitacion?>(null) }

    // Selector nativo de la galería (sin permisos de almacenamiento). Si se cancela, uri llega null.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            viewModel.onFilePicked(uri, if (pickingVideo) TipoHabitacion.VIDEO else TipoHabitacion.IMAGEN)
        }
    }

    LaunchedEffect(viewModel.error) {
        viewModel.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

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
            floatingActionButton = {
                FloatingActionButton(onClick = { showTypeDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.rooms_add))
                }
            },
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.rooms_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.rooms_close))
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // El espacio inferior evita que el botón "+" tape la última fila.
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item { SectionTitle(stringResource(R.string.rooms_official)) }
                    items(viewModel.oficiales, key = { it.id }) { room ->
                        RoomRow(
                            habitacion = room,
                            active = room.id == viewModel.activeId,
                            onClick = {
                                viewModel.select(room.id)
                                onClose()
                            },
                            onDelete = null, // las oficiales no se eliminan
                        )
                    }

                    item { SectionTitle(stringResource(R.string.rooms_mine)) }
                    if (viewModel.propias.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.rooms_mine_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    }
                    items(viewModel.propias, key = { it.id }) { room ->
                        RoomRow(
                            habitacion = room,
                            active = room.id == viewModel.activeId,
                            onClick = {
                                viewModel.select(room.id)
                                onClose()
                            },
                            onDelete = { deleting = room },
                        )
                    }
                }
            }
        }

        // Mientras se copia el archivo (videos grandes) se bloquea la pantalla con un aviso.
        if (viewModel.copying) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .pointerInput(Unit) { detectTapGestures { } },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.rooms_copying), color = Color.White)
                }
            }
        }
    }

    // 1) ¿Imagen o Video?
    if (showTypeDialog) {
        AlertDialog(
            onDismissRequest = { showTypeDialog = false },
            title = { Text(stringResource(R.string.rooms_type_title)) },
            confirmButton = {
                TextButton(onClick = {
                    showTypeDialog = false
                    pickingVideo = false
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text(stringResource(R.string.rooms_type_image)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showTypeDialog = false
                    pickingVideo = true
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                }) { Text(stringResource(R.string.rooms_type_video)) }
            },
        )
    }

    // 2) Nombre de la habitación (el archivo ya está copiado)
    viewModel.pending?.let {
        var name by rememberSaveable(it.path) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = viewModel::cancelPending,
            title = { Text(stringResource(R.string.rooms_name_title)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { value -> if (value.length <= MAX_NAME_LENGTH) name = value },
                    label = { Text(stringResource(R.string.rooms_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.saveRoom(name) }, enabled = name.isNotBlank()) {
                    Text(stringResource(R.string.rooms_save))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelPending) { Text(stringResource(R.string.rooms_cancel)) }
            },
        )
    }

    // 3) Confirmar eliminar
    deleting?.let { room ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.rooms_delete_title)) },
            text = { Text(stringResource(R.string.rooms_delete_message, room.nombre)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(room)
                    deleting = null
                }) { Text(stringResource(R.string.rooms_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.rooms_cancel)) }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun RoomRow(habitacion: Habitacion, active: Boolean, onClick: () -> Unit, onDelete: (() -> Unit)?) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoomThumbnail(habitacion)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = habitacion.nombre,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(
                        if (habitacion.tipo == TipoHabitacion.VIDEO) R.string.rooms_type_video else R.string.rooms_type_image
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            if (active) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = stringResource(R.string.rooms_active),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.rooms_delete))
                }
            }
        }
    }
}

/** Miniatura: la imagen, o un ícono de "play" para los videos (no se cargan fotogramas para ahorrar recursos). */
@Composable
private fun RoomThumbnail(habitacion: Habitacion) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .size(width = 72.dp, height = 48.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        when {
            habitacion.recurso != 0 -> Image(
                painter = painterResource(habitacion.recurso),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            habitacion.tipo == TipoHabitacion.IMAGEN -> AsyncImage(
                model = File(habitacion.rutaArchivo),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            else -> Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
