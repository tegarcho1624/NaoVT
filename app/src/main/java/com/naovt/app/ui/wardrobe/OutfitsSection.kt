package com.naovt.app.ui.wardrobe

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.naovt.app.R
import com.naovt.app.wardrobe.Outfit
import com.naovt.app.wardrobe.WardrobeBitmaps
import com.naovt.app.wardrobe.WardrobeViewModel
import kotlin.math.abs

private const val MAX_NAME_LENGTH = 30
private val CARD_WIDTH = 72.dp
private val CARD_PREVIEW_HEIGHT = 96.dp

/**
 * Estado del arrastre de outfits. [order] (ids) es el orden visible y se reordena en vivo mientras se
 * arrastra; al soltar se guarda. Misma técnica que el Gestor de Capas, pero en horizontal.
 */
private class OutfitDragState(
    val listState: LazyListState,
    initialOrder: List<String>,
) {
    var order by mutableStateOf(initialOrder)
    var dragged by mutableStateOf<String?>(null)
    var dragOffset by mutableFloatStateOf(0f)

    fun start(id: String) {
        dragged = id
        dragOffset = 0f
    }

    fun dragBy(dx: Float) {
        dragOffset += dx
        swapIfNeeded()
    }

    /** Si el centro de la tarjeta arrastrada pasó sobre otra, intercambian lugares. */
    fun swapIfNeeded() {
        val id = dragged ?: return
        val infos = listState.layoutInfo.visibleItemsInfo
        val current = infos.firstOrNull { it.key == id } ?: return
        val center = current.offset + dragOffset + current.size / 2f
        val target = infos.firstOrNull {
            it.key != id && center >= it.offset && center < it.offset + it.size
        } ?: return
        val from = order.indexOf(id)
        val to = order.indexOf(target.key as? String ?: return)
        if (from < 0 || to < 0 || from == to) return
        order = order.toMutableList().apply { add(to, removeAt(from)) }
        // La tarjeta cambió de lugar: se compensa para que siga bajo el dedo.
        dragOffset += current.offset - target.offset
    }

    fun finish(): List<String> {
        dragged = null
        dragOffset = 0f
        return order
    }
}

/**
 * "Mis Outfits": el botón para guardar el atuendo actual y una fila horizontal con los conjuntos
 * guardados. Tocar uno viste a Nao con él; mantenerlo presionado y arrastrarlo lo reordena;
 * mantenerlo presionado sin arrastrar ofrece cambiarle el nombre o eliminarlo.
 */
@Composable
fun OutfitsSection(viewModel: WardrobeViewModel, modifier: Modifier = Modifier) {
    var saving by rememberSaveable { mutableStateOf(false) }
    // Mantener presionado un outfit abre sus opciones; de ahí se pasa a renombrar o a eliminar.
    var options by remember { mutableStateOf<Outfit?>(null) }
    var renaming by remember { mutableStateOf<Outfit?>(null) }
    var deleting by remember { mutableStateOf<Outfit?>(null) }
    val outfits = viewModel.outfits

    Column(modifier = modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.wardrobe_outfits_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            FilledTonalButton(onClick = { saving = true }) {
                Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.wardrobe_save_outfit))
            }
        }

        if (outfits.isEmpty()) {
            Text(
                text = stringResource(R.string.wardrobe_outfits_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        } else {
            val listState = rememberLazyListState()
            val ids = outfits.map { it.id }
            // Si cambia la lista (guardar, eliminar, guardar un orden) el estado se arma de nuevo.
            val drag = remember(ids) { OutfitDragState(listState, ids) }
            val byId = remember(outfits) { outfits.associateBy { it.id } }
            val haptics = LocalHapticFeedback.current
            val edgePx = with(LocalDensity.current) { 48.dp.toPx() }

            // Mientras se arrastra cerca de un borde, la fila se desplaza sola.
            LaunchedEffect(drag.dragged) {
                while (true) {
                    val id = drag.dragged ?: break
                    val layout = listState.layoutInfo
                    val info = layout.visibleItemsInfo.firstOrNull { it.key == id }
                    if (info != null) {
                        val start = info.offset + drag.dragOffset
                        val end = start + info.size
                        val delta = when {
                            start < layout.viewportStartOffset + edgePx -> -14f
                            end > layout.viewportEndOffset - edgePx -> 14f
                            else -> 0f
                        }
                        if (delta != 0f) {
                            drag.dragOffset += listState.scrollBy(delta)
                            drag.swapIfNeeded()
                        }
                    }
                    kotlinx.coroutines.delay(16)
                }
            }

            LazyRow(
                state = listState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(drag.order, key = { it }) { id ->
                    val outfit = byId[id]
                    if (outfit != null) {
                        val dragging = drag.dragged == id
                        OutfitCard(
                            outfit = outfit,
                            viewModel = viewModel,
                            drag = drag,
                            haptics = haptics,
                            onApply = { viewModel.applyOutfit(outfit) },
                            onLongPress = { options = outfit },
                            onReordered = { newOrder -> viewModel.moveOutfits(newOrder) },
                            modifier = if (dragging) {
                                Modifier
                                    .zIndex(1f)
                                    .graphicsLayer {
                                        translationX = drag.dragOffset
                                        scaleX = 1.06f
                                        scaleY = 1.06f
                                    }
                            } else {
                                Modifier.animateItem()
                            },
                        )
                    }
                }
            }
        }
    }

    if (saving) {
        OutfitNameDialog(
            title = stringResource(R.string.wardrobe_outfit_name_title),
            initialName = stringResource(R.string.wardrobe_outfit_default_name, outfits.size + 1),
            confirmLabel = stringResource(R.string.wardrobe_save),
            onDismiss = { saving = false },
            onConfirm = { name ->
                viewModel.saveOutfit(name)
                saving = false
            },
        )
    }

    options?.let { outfit ->
        AlertDialog(
            onDismissRequest = { options = null },
            title = { Text(outfit.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            renaming = outfit
                            options = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.wardrobe_outfit_rename), modifier = Modifier.fillMaxWidth()) }
                    TextButton(
                        onClick = {
                            deleting = outfit
                            options = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.wardrobe_outfit_delete_confirm), modifier = Modifier.fillMaxWidth()) }
                }
            },
            confirmButton = {
                TextButton(onClick = { options = null }) { Text(stringResource(R.string.wardrobe_cancel)) }
            },
        )
    }

    renaming?.let { outfit ->
        OutfitNameDialog(
            title = stringResource(R.string.wardrobe_outfit_rename_title),
            initialName = outfit.name,
            confirmLabel = stringResource(R.string.wardrobe_save),
            onDismiss = { renaming = null },
            onConfirm = { name ->
                viewModel.renameOutfit(outfit, name)
                renaming = null
            },
        )
    }

    deleting?.let { outfit ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.wardrobe_outfit_delete_title)) },
            text = { Text(stringResource(R.string.wardrobe_outfit_delete_message, outfit.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteOutfit(outfit)
                    deleting = null
                }) { Text(stringResource(R.string.wardrobe_outfit_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.wardrobe_cancel)) }
            },
        )
    }
}

/** Tarjeta de un outfit: vista previa reducida de Nao con ese atuendo y su nombre. */
@Composable
private fun OutfitCard(
    outfit: Outfit,
    viewModel: WardrobeViewModel,
    drag: OutfitDragState,
    haptics: HapticFeedback,
    onApply: () -> Unit,
    onLongPress: () -> Unit,
    onReordered: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)

    // Tras mantener presionado, al soltar no debe contarse además como un toque (vestir).
    var suppressTap by remember { mutableStateOf(false) }
    val currentOnApply by rememberUpdatedState(onApply)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentOnReordered by rememberUpdatedState(onReordered)

    Column(
        modifier = modifier
            .width(CARD_WIDTH)
            .pointerInput(outfit.id) {
                detectTapGestures(
                    onPress = { suppressTap = false },
                    onTap = { if (!suppressTap) currentOnApply() },
                )
            }
            .pointerInput(drag, outfit.id) {
                var moved = 0f
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        suppressTap = true
                        moved = 0f
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        drag.start(outfit.id)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        moved += abs(amount.x) + abs(amount.y)
                        drag.dragBy(amount.x)
                    },
                    onDragEnd = {
                        val newOrder = drag.finish()
                        // Sin arrastrar = pulsación larga: ofrece renombrar o eliminar. Arrastrando: solo reordena.
                        if (moved < viewConfiguration.touchSlop) currentOnLongPress() else currentOnReordered(newOrder)
                    },
                    onDragCancel = { drag.finish() },
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(CARD_PREVIEW_HEIGHT)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), shape),
        ) {
            OutfitPreviewImage(outfit, viewModel, Modifier.fillMaxSize().padding(2.dp))
        }
        Text(
            text = outfit.name,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Pide el nombre de un conjunto (para guardarlo o cambiarlo). Solo permite aceptar si no está vacío. */
@Composable
private fun OutfitNameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_NAME_LENGTH) },
                singleLine = true,
                label = { Text(stringResource(R.string.wardrobe_outfit_name_hint)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.wardrobe_cancel)) } },
    )
}
