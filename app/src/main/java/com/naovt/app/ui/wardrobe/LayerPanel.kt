package com.naovt.app.ui.wardrobe

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.naovt.app.R
import com.naovt.app.wardrobe.CharacterConfig
import com.naovt.app.wardrobe.WardrobeBitmaps
import com.naovt.app.wardrobe.WardrobeItem
import com.naovt.app.wardrobe.WardrobeSlot

private val ROW_HEIGHT = 56.dp
private val ROW_SPACING = 4.dp
private val AUTO_SCROLL_EDGE = 40.dp
private const val AUTO_SCROLL_STEP_PX = 14f

/**
 * Estado del arrastre. [order] es la lista que se ve (de arriba hacia abajo = de la capa de más
 * adelante a la de más atrás) y se reordena en vivo mientras se arrastra; el cambio real de Nao se
 * aplica al soltar.
 *
 * [dragOffset] es cuánto se ha movido el dedo respecto a donde está acomodada la fila arrastrada.
 * Cuando la fila cambia de lugar en la lista, se compensa para que siga pegada al dedo.
 */
private class LayerDragState(
    val listState: LazyListState,
    initialOrder: List<WardrobeSlot>,
) {
    var order by mutableStateOf(initialOrder)
    var dragged by mutableStateOf<WardrobeSlot?>(null)
    var dragOffset by mutableFloatStateOf(0f)

    fun start(slot: WardrobeSlot) {
        dragged = slot
        dragOffset = 0f
    }

    fun dragBy(dy: Float) {
        dragOffset += dy
        swapIfNeeded()
    }

    /** Si el centro de la fila arrastrada pasó sobre otra fila, intercambian lugares. */
    fun swapIfNeeded() {
        val slot = dragged ?: return
        val infos = listState.layoutInfo.visibleItemsInfo
        val current = infos.firstOrNull { it.key == slot.name } ?: return
        val center = current.offset + dragOffset + current.size / 2f
        val target = infos.firstOrNull {
            it.key != slot.name && center >= it.offset && center < it.offset + it.size
        } ?: return
        val targetSlot = order.firstOrNull { it.name == target.key } ?: return
        val from = order.indexOf(slot)
        val to = order.indexOf(targetSlot)
        if (from < 0 || to < 0 || from == to) return
        order = order.toMutableList().apply { add(to, removeAt(from)) }
        // La fila cambió de lugar: se compensa para que siga bajo el dedo.
        dragOffset += current.offset - target.offset
    }

    fun finish(): List<WardrobeSlot> {
        dragged = null
        dragOffset = 0f
        return order
    }
}

/**
 * Gestor de Capas: lista SOLO lo que Nao lleva puesto, de la capa de más adelante (arriba) a la de
 * más atrás (abajo). Mantener presionadas las tres rayas de una fila y arrastrarla cambia el orden
 * de dibujo; al soltar se llama a [onReorder] con las capas de atrás hacia adelante.
 */
@Composable
fun LayerPanel(
    config: CharacterConfig,
    onReorder: (visibleBottomToTop: List<WardrobeSlot>) -> Unit,
    modifier: Modifier = Modifier,
) {
    // De arriba hacia abajo en pantalla = de adelante hacia atrás.
    val rows = config.visibleSlots().reversed()
    val listState = rememberLazyListState()
    // Si cambia lo equipado (otra camisa, quitar un gorro...) la lista se vuelve a armar sola.
    val drag = remember(rows) { LayerDragState(listState, rows) }
    val haptics = LocalHapticFeedback.current

    // Mientras se arrastra cerca del borde, la lista se desplaza sola.
    val edgePx = with(androidx.compose.ui.platform.LocalDensity.current) { AUTO_SCROLL_EDGE.toPx() }
    LaunchedEffect(drag.dragged) {
        if (drag.dragged == null) return@LaunchedEffect
        while (true) {
            val slot = drag.dragged ?: break
            val layout = listState.layoutInfo
            val info = layout.visibleItemsInfo.firstOrNull { it.key == slot.name }
            if (info != null) {
                val top = info.offset + drag.dragOffset
                val bottom = top + info.size
                val delta = when {
                    top < layout.viewportStartOffset + edgePx -> -AUTO_SCROLL_STEP_PX
                    bottom > layout.viewportEndOffset - edgePx -> AUTO_SCROLL_STEP_PX
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

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(top = 10.dp),
    ) {
        Text(
            text = stringResource(R.string.wardrobe_layers),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 14.dp, bottom = 6.dp),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 6.dp, end = 6.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(ROW_SPACING),
        ) {
            items(drag.order, key = { it.name }) { slot ->
                val isDragged = drag.dragged == slot
                val file = config.fileFor(slot)
                val path = config.assetPathFor(slot)
                val item = if (file != null && path != null) WardrobeItem(slot, file, path) else null
                if (item != null) {
                    LayerRow(
                        item = item,
                        dragging = isDragged,
                        modifier = if (isDragged) {
                            Modifier
                                .zIndex(1f)
                                .graphicsLayer { translationY = drag.dragOffset }
                        } else {
                            Modifier.animateItem()
                        },
                        handleModifier = Modifier.pointerInput(drag, slot) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    drag.start(slot)
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    drag.dragBy(amount.y)
                                },
                                onDragEnd = { onReorder(drag.finish().reversed()) },
                                onDragCancel = { drag.finish() },
                            )
                        },
                    )
                }
            }
        }
    }
}

/** Una fila: miniatura, nombre y, a la derecha, las tres rayas que sirven de agarradera. */
@Composable
private fun LayerRow(
    item: WardrobeItem,
    dragging: Boolean,
    modifier: Modifier,
    handleModifier: Modifier,
) {
    val context = LocalContext.current
    val thumbnail by produceState<ImageBitmap?>(
        initialValue = WardrobeBitmaps.cachedThumbnail(item),
        key1 = item.assetPath,
    ) {
        value = WardrobeBitmaps.thumbnail(context, item)
    }
    val shape = RoundedCornerShape(12.dp)
    val background = if (dragging) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.White.copy(alpha = 0.05f)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .then(if (dragging) Modifier.shadow(8.dp, shape) else Modifier)
            .clip(shape)
            .background(background)
            .padding(start = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.25f)),
        ) {
            thumbnail?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(2.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = handleModifier.size(width = 44.dp, height = ROW_HEIGHT),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Menu,
                contentDescription = stringResource(R.string.wardrobe_drag_handle),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}
