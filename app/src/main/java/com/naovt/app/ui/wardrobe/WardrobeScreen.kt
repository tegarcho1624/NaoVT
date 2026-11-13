package com.naovt.app.ui.wardrobe

import androidx.activity.compose.BackHandler
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
                        PreviewWithLayers(viewModel, Modifier.weight(0.5f).fillMaxHeight())
                        Column(modifier = Modifier.weight(0.5f).fillMaxHeight()) {
                            CategoryTabs(selectedTab) { selectedTab = it }
                            AssetGrid(viewModel, slot, Modifier.weight(1f))
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        PreviewWithLayers(viewModel, Modifier.weight(0.45f).fillMaxWidth())
                        CategoryTabs(selectedTab) { selectedTab = it }
                        AssetGrid(viewModel, slot, Modifier.weight(0.55f))
                    }
                }
            }
        }
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

/** Cuadrícula de la categoría: [Ninguno], todos sus assets y, al final, el recuadro "+ Próximamente". */
@Composable
private fun AssetGrid(viewModel: WardrobeViewModel, slot: WardrobeSlot, modifier: Modifier = Modifier) {
    val items = viewModel.catalog.itemsFor(slot)
    val equipped = viewModel.config.fileFor(slot)

    // Cada categoría tiene su propia cuadrícula (y su propio desplazamiento).
    androidx.compose.runtime.key(slot) {
        LazyVerticalGrid(
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
            items(items, key = { it.fileName }) { asset ->
                AssetCell(
                    item = asset,
                    selected = asset.fileName == equipped,
                    onClick = { viewModel.equip(slot, asset.fileName) },
                )
            }
            item(key = "coming_soon") { ComingSoonCell() }
        }
    }
}

@Composable
private fun AssetCell(item: WardrobeItem, selected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val thumbnail by produceState<ImageBitmap?>(
        initialValue = WardrobeBitmaps.cachedThumbnail(item),
        key1 = item.assetPath,
    ) {
        value = WardrobeBitmaps.thumbnail(context, item)
    }

    CellFrame(selected = selected, label = item.label, onClick = onClick) {
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
    CellFrame(selected = selected, label = label, onClick = onClick) {
        Icon(
            Icons.Default.Close,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.Center).size(32.dp),
        )
        if (selected) CheckBadge(Modifier.align(Alignment.TopEnd))
    }
}

/** Recuadro de relleno al final de cada lista: por ahora no hace nada. */
@Composable
private fun ComingSoonCell() {
    val dashColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
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
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.size(36.dp),
            )
        }
        Text(
            text = stringResource(R.string.wardrobe_coming_soon),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Marco común de una celda: recuadro cuadrado con borde (resaltado si está equipada) y su nombre debajo. */
@Composable
private fun CellFrame(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    },
                    shape = shape,
                )
                .clickable(onClick = onClick),
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
