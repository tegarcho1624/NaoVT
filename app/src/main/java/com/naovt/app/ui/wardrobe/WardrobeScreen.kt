package com.naovt.app.ui.wardrobe

import androidx.activity.compose.BackHandler
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import kotlin.math.abs
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.naovt.app.R
import com.naovt.app.ui.avatar.NaoExpression
import com.naovt.app.ui.avatar.NaoLayeredAvatar
import com.naovt.app.wardrobe.WardrobeBitmaps
import com.naovt.app.wardrobe.WardrobeItem
import com.naovt.app.wardrobe.WardrobeSlot
import com.naovt.app.wardrobe.WardrobeViewModel

private val CELL_MIN_SIZE = 104.dp
private val LAYER_PANEL_WIDTH = 190.dp

/**
 * El Armario: arriba (o a la izquierda en pantallas anchas) la vista previa de Nao, y debajo una
 * fila de pestañas con las categorías y la cuadrícula de assets de la categoría elegida.
 * Tocar un asset lo equipa al instante; el cambio queda guardado y se ve en la pantalla principal.
 */
@Composable
fun WardrobeScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WardrobeViewModel = viewModel(),
) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val tabs = WardrobeSlot.tabs
    val slot = tabs[selectedTab.coerceIn(0, tabs.lastIndex)]

    val context = LocalContext.current
    var importSlotName by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<WardrobeItem?>(null) }

    // Selector nativo de imágenes (sin permisos de almacenamiento). Si se cancela, uri llega null.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val target = WardrobeSlot.entries.firstOrNull { it.name == importSlotName }
        if (uri != null && target != null) viewModel.importAsset(target, uri)
        importSlotName = null
    }
    val onImport: () -> Unit = {
        importSlotName = slot.name
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    LaunchedEffect(viewModel.message) {
        viewModel.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearMessage()
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
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.wardrobe_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.wardrobe_close))
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                val wide = maxWidth > maxHeight
                if (wide) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.weight(0.5f).fillMaxHeight()) {
                            PreviewWithLayers(viewModel, Modifier.weight(1f).fillMaxWidth())
                            OutfitsSection(viewModel)
                        }
                        Column(modifier = Modifier.weight(0.5f).fillMaxHeight()) {
                            CategoryTabs(selectedTab) { selectedTab = it }
                            AssetGrid(viewModel, slot, onImport, { deleting = it }, Modifier.weight(1f))
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        PreviewWithLayers(viewModel, Modifier.weight(0.45f).fillMaxWidth())
                        OutfitsSection(viewModel)
                        CategoryTabs(selectedTab) { selectedTab = it }
                        AssetGrid(viewModel, slot, onImport, { deleting = it }, Modifier.weight(0.55f))
                    }
                }
            }
        }
    }

    // La galería no dio el nombre real de la imagen: se le pide al usuario.
    viewModel.pendingImport?.let { pending ->
        var assetName by rememberSaveable(pending.uri.toString()) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { viewModel.cancelImportName() },
            title = { Text(stringResource(R.string.wardrobe_import_name_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.wardrobe_import_name_message))
                    OutlinedTextField(
                        value = assetName,
                        onValueChange = { assetName = it.take(60) },
                        singleLine = true,
                        label = { Text(stringResource(R.string.wardrobe_import_name_hint)) },
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmImportName(assetName) },
                    enabled = assetName.isNotBlank(),
                ) { Text(stringResource(R.string.wardrobe_import)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelImportName() }) { Text(stringResource(R.string.wardrobe_cancel)) }
            },
        )
    }

    // Eliminar un asset personalizado (los nativos nunca llegan aquí).
    deleting?.let { asset ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.wardrobe_delete_title)) },
            text = { Text(stringResource(R.string.wardrobe_delete_message, asset.label)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAsset(asset)
                    deleting = null
                }) { Text(stringResource(R.string.wardrobe_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.wardrobe_cancel)) }
            },
        )
    }
}

/** La vista previa de Nao con el Gestor de Capas en el costado derecho. */
@Composable
private fun PreviewWithLayers(viewModel: WardrobeViewModel, modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
        Preview(viewModel, Modifier.weight(1f).fillMaxHeight())
        LayerPanel(
            config = viewModel.config,
            onReorder = viewModel::reorder,
            modifier = Modifier
                .width(LAYER_PANEL_WIDTH)
                .fillMaxHeight()
                .padding(end = 16.dp, top = 4.dp, bottom = 4.dp),
        )
    }
}

/** Nao en tiempo real con lo que lleva puesto; se actualiza en cuanto se equipa algo. */
@Composable
private fun Preview(viewModel: WardrobeViewModel, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.BottomCenter,
    ) {
        NaoLayeredAvatar(
            config = viewModel.config,
            expression = NaoExpression.Neutral,
            modifier = Modifier.fillMaxHeight().padding(top = 8.dp),
        )
    }
}

/** Pestañas de categorías: una fila horizontal desplazable. */
@Composable
private fun CategoryTabs(selectedTab: Int, onSelect: (Int) -> Unit) {
    ScrollableTabRow(
        selectedTabIndex = selectedTab.coerceIn(0, WardrobeSlot.tabs.lastIndex),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.primary,
        edgePadding = 12.dp,
    ) {
        WardrobeSlot.tabs.forEachIndexed { index, tabSlot ->
            Tab(
                selected = index == selectedTab,
                onClick = { onSelect(index) },
                text = { Text(stringResource(tabSlot.label), maxLines = 1) },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
        }
    }
}

/**
 * Estado del arrastre de assets en la cuadrícula. [order] es el orden visible y se reordena en vivo
 * mientras se arrastra; al soltar se guarda. Solo cambia el orden de la cuadrícula, nunca las capas.
 * Misma técnica que el Gestor de Capas, pero en dos dimensiones.
 */
private class GridDragState(
    val gridState: LazyGridState,
    initialOrder: List<String>,
) {
    var order by mutableStateOf(initialOrder)
    var dragged by mutableStateOf<String?>(null)
    var dragOffset by mutableStateOf(Offset.Zero)

    fun start(id: String) {
        dragged = id
        dragOffset = Offset.Zero
    }

    fun dragBy(delta: Offset) {
        dragOffset += delta
        swapIfNeeded()
    }

    /** Si el centro de la celda arrastrada pasó sobre otro asset, intercambian lugares. */
    fun swapIfNeeded() {
        val id = dragged ?: return
        val infos = gridState.layoutInfo.visibleItemsInfo
        val current = infos.firstOrNull { it.key == id } ?: return
        val centerX = current.offset.x + dragOffset.x + current.size.width / 2f
        val centerY = current.offset.y + dragOffset.y + current.size.height / 2f
        val target = infos.firstOrNull {
            val key = it.key as? String
            key != null && key != id && key in order &&
                centerX >= it.offset.x && centerX < it.offset.x + it.size.width &&
                centerY >= it.offset.y && centerY < it.offset.y + it.size.height
        } ?: return
        val from = order.indexOf(id)
        val to = order.indexOf(target.key as String)
        if (from < 0 || to < 0 || from == to) return
        order = order.toMutableList().apply { add(to, removeAt(from)) }
        // La celda cambió de lugar: se compensa para que siga bajo el dedo.
        dragOffset += Offset(
            (current.offset.x - target.offset.x).toFloat(),
            (current.offset.y - target.offset.y).toFloat(),
        )
    }

    fun finish(): List<String> {
        dragged = null
        dragOffset = Offset.Zero
        return order
    }
}

/**
 * Cuadrícula de la categoría: [Ninguno], todos sus assets (nativos e importados) y, al final, el
 * recuadro "+" para importar.
 * - Tocar un asset lo equipa.
 * - Mantener presionado y arrastrar lo reordena (solo el orden de esta cuadrícula).
 * - Mantener presionado sin arrastrar, en un asset importado, ofrece eliminarlo.
 */
@Composable
private fun AssetGrid(
    viewModel: WardrobeViewModel,
    slot: WardrobeSlot,
    onImport: () -> Unit,
    onRequestDelete: (WardrobeItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = viewModel.itemsFor(slot)
    val equipped = viewModel.config.fileFor(slot)

    // Cada categoría tiene su propia cuadrícula (y su propio desplazamiento).
    key(slot) {
        val gridState = rememberLazyGridState()
        val ids = items.map { it.fileName }
        // Si cambia la lista (importar, eliminar, guardar un orden) el estado se arma de nuevo.
        val drag = remember(ids) { GridDragState(gridState, ids) }
        val byId = remember(items) { items.associateBy { it.fileName } }
        val haptics = LocalHapticFeedback.current
        val edgePx = with(LocalDensity.current) { 56.dp.toPx() }

        // Mientras se arrastra cerca del borde superior o inferior, la cuadrícula se desplaza sola.
        LaunchedEffect(drag.dragged) {
            while (true) {
                val id = drag.dragged ?: break
                val layout = gridState.layoutInfo
                val info = layout.visibleItemsInfo.firstOrNull { it.key == id }
                if (info != null) {
                    val top = info.offset.y + drag.dragOffset.y
                    val bottom = top + info.size.height
                    val delta = when {
                        top < layout.viewportStartOffset + edgePx -> -14f
                        bottom > layout.viewportEndOffset - edgePx -> 14f
                        else -> 0f
                    }
                    if (delta != 0f) {
                        val consumed = gridState.scrollBy(delta)
                        drag.dragOffset += Offset(0f, consumed)
                        drag.swapIfNeeded()
                    }
                }
                kotlinx.coroutines.delay(16)
            }
        }

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(CELL_MIN_SIZE),
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (slot.optional) {
                item(key = "none") {
                    NoneCell(selected = equipped == null, onClick = { viewModel.equip(slot, null) })
                }
            }
            items(drag.order, key = { it }) { id ->
                val asset = byId[id]
                if (asset != null) {
                    val dragging = drag.dragged == id
                    AssetCell(
                        item = asset,
                        selected = id == equipped,
                        dragging = dragging,
                        drag = drag,
                        haptics = haptics,
                        onClick = { viewModel.equip(slot, id) },
                        onLongPressWithoutDrag = { if (asset.isCustom) onRequestDelete(asset) },
                        onReordered = { newOrder -> viewModel.moveAssets(slot, newOrder) },
                        modifier = if (dragging) {
                            Modifier
                                .zIndex(1f)
                                .graphicsLayer {
                                    translationX = drag.dragOffset.x
                                    translationY = drag.dragOffset.y
                                    scaleX = 1.06f
                                    scaleY = 1.06f
                                }
                        } else {
                            Modifier.animateItem()
                        },
                    )
                }
            }
            item(key = "import") { ImportCell(onClick = onImport) }
        }
    }
}

@Composable
private fun AssetCell(
    item: WardrobeItem,
    selected: Boolean,
    dragging: Boolean,
    drag: GridDragState,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onClick: () -> Unit,
    onLongPressWithoutDrag: () -> Unit,
    onReordered: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val thumbnail by produceState<ImageBitmap?>(
        initialValue = WardrobeBitmaps.cachedThumbnail(item),
        key1 = item.assetPath,
    ) {
        value = WardrobeBitmaps.thumbnail(context, item)
    }
    // Tras mantener presionado, al soltar no debe contarse además como un toque (equipar).
    var suppressTap by remember { mutableStateOf(false) }
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongPress by rememberUpdatedState(onLongPressWithoutDrag)
    val currentOnReordered by rememberUpdatedState(onReordered)

    val gestures = Modifier
        .pointerInput(item.fileName) {
            detectTapGestures(
                onPress = { suppressTap = false },
                onTap = { if (!suppressTap) currentOnClick() },
            )
        }
        .pointerInput(drag, item.fileName) {
            var moved = 0f
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    suppressTap = true
                    moved = 0f
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    drag.start(item.fileName)
                },
                onDrag = { change, amount ->
                    change.consume()
                    moved += abs(amount.x) + abs(amount.y)
                    drag.dragBy(amount)
                },
                onDragEnd = {
                    val newOrder = drag.finish()
                    if (moved < viewConfiguration.touchSlop) currentOnLongPress() else currentOnReordered(newOrder)
                },
                onDragCancel = { drag.finish() },
            )
        }

    CellFrame(
        selected = selected,
        label = item.label,
        modifier = modifier,
        highlighted = dragging,
        interaction = gestures,
    ) {
        thumbnail?.let {
            Image(
                bitmap = it,
                contentDescription = item.label,
                modifier = Modifier.fillMaxSize().padding(6.dp),
                contentScale = ContentScale.Fit,
            )
        }
        if (selected) CheckBadge(Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun NoneCell(selected: Boolean, onClick: () -> Unit) {
    val label = stringResource(R.string.wardrobe_none)
    CellFrame(selected = selected, label = label, interaction = Modifier.clickable(onClick = onClick)) {
        Icon(
            Icons.Default.Close,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.Center).size(32.dp),
        )
        if (selected) CheckBadge(Modifier.align(Alignment.TopEnd))
    }
}

/** Recuadro "+" al final de cada lista: abre el selector de imágenes para importar un asset. */
@Composable
private fun ImportCell(onClick: () -> Unit) {
    val dashColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    val shape = RoundedCornerShape(14.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .clickable(onClick = onClick)
                .drawBehind {
                    drawRoundRect(
                        color = dashColor,
                        cornerRadius = CornerRadius(14.dp.toPx()),
                        style = Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f)),
                        ),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.size(36.dp),
            )
        }
        Text(
            text = stringResource(R.string.wardrobe_import),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * Marco común de una celda: recuadro cuadrado con borde (resaltado si está equipada) y su nombre
 * debajo. [interaction] trae los gestos de cada tipo de celda.
 */
@Composable
private fun CellFrame(
    selected: Boolean,
    label: String,
    interaction: Modifier,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .background(
                    if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                )
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    },
                    shape = shape,
                )
                .then(interaction),
            content = content,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (selected) 1f else 0.7f),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun CheckBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(6.dp)
            .size(20.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(14.dp),
        )
    }
}
