package com.naovt.app.ui.gestures

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
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
import com.naovt.app.gestures.GestureGroup
import com.naovt.app.gestures.GestureSettings
import com.naovt.app.gestures.GesturesViewModel
import com.naovt.app.ui.wardrobe.OutfitPreviewImage
import com.naovt.app.wardrobe.Outfit
import com.naovt.app.wardrobe.WardrobeBitmaps
import com.naovt.app.wardrobe.WardrobeItem
import com.naovt.app.wardrobe.WardrobeSlot
import com.naovt.app.wardrobe.WardrobeViewModel
import kotlin.math.roundToInt

private enum class GestureSection(
    @StringRes val title: Int,
    @StringRes val description: Int,
    val group: GestureGroup,
) {
    Blink(R.string.gestures_blink, R.string.gestures_blink_desc, GestureGroup.Blink),
    Talk(R.string.gestures_talk, R.string.gestures_talk_desc, GestureGroup.Talk),
    Kiss(R.string.gestures_kiss, R.string.gestures_kiss_desc, GestureGroup.Kiss),
}

/**
 * Lo que editan las secciones Pestañeo / Hablar / Beso: los valores actuales, cómo cambiarlos y cómo
 * restablecer un grupo. Hay uno para los gestos globales y otro para los de un outfit concreto, así
 * la misma interfaz sirve para los dos.
 */
private class GestureEditor(
    val settings: GestureSettings,
    val update: ((GestureSettings) -> GestureSettings) -> Unit,
    val reset: (GestureGroup) -> Unit,
)

/**
 * Menú de Gestos: una lista con los gestos de Nao y, dentro de cada uno, la edición de sus ojos,
 * su boca y (en el pestañeo) frecuencia y duración. Todo se guarda al instante y se aplica sin
 * reiniciar: el siguiente parpadeo, frase o beso ya usa lo nuevo.
 *
 * "Gestos de Conjuntos" abre una cuadrícula con los outfits guardados; al tocar uno se edita lo mismo,
 * pero solo para ese outfit.
 */
@Composable
fun GesturesScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    gesturesViewModel: GesturesViewModel = viewModel(),
    wardrobeViewModel: WardrobeViewModel = viewModel(),
) {
    var sectionName by rememberSaveable { mutableStateOf<String?>(null) }
    var outfitsOpen by rememberSaveable { mutableStateOf(false) }
    var outfitId by rememberSaveable { mutableStateOf<String?>(null) }

    val section = GestureSection.entries.firstOrNull { it.name == sectionName }
    val outfit = outfitId?.let { id -> wardrobeViewModel.outfits.firstOrNull { it.id == id } }
    // Si el outfit se eliminó mientras estaba abierto, se vuelve a la cuadrícula.
    if (outfitId != null && outfit == null) outfitId = null

    val atRoot = !outfitsOpen && section == null
    val goBack: () -> Unit = {
        when {
            section != null -> sectionName = null
            outfit != null -> outfitId = null
            outfitsOpen -> outfitsOpen = false
            else -> onClose()
        }
    }

    BackHandler(onBack = goBack)

    val editor: GestureEditor = if (outfit == null) {
        GestureEditor(
            settings = gesturesViewModel.settings,
            update = { transform -> gesturesViewModel.update(transform) },
            reset = { group -> gesturesViewModel.reset(group) },
        )
    } else {
        // Un outfit sin gestos propios parte de los globales: al primer cambio se guarda su copia.
        val current = outfit.gestures ?: gesturesViewModel.settings
        GestureEditor(
            settings = current,
            update = { transform ->
                val latest = wardrobeViewModel.outfits.firstOrNull { it.id == outfit.id }
                    ?.let { it.gestures ?: gesturesViewModel.settings } ?: current
                wardrobeViewModel.setOutfitGestures(outfit.id, transform(latest))
            },
            reset = { group ->
                val latest = wardrobeViewModel.outfits.firstOrNull { it.id == outfit.id }
                    ?.let { it.gestures ?: gesturesViewModel.settings } ?: current
                wardrobeViewModel.setOutfitGestures(outfit.id, latest.withDefaults(group))
            },
        )
    }

    val titleText = when {
        section != null -> stringResource(section.title)
        outfit != null -> outfit.name
        outfitsOpen -> stringResource(R.string.gestures_outfits)
        else -> stringResource(R.string.gestures_title)
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            // Evita que los toques atraviesen y lleguen a la pantalla de abajo.
            .pointerInput(Unit) { detectTapGestures { } },
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = goBack) {
                    Icon(
                        if (atRoot) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.gestures_back),
                    )
                }
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (outfitsOpen && outfit == null) {
                OutfitGrid(
                    wardrobe = wardrobeViewModel,
                    onPick = { outfitId = it.id },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (section == null) {
                        if (outfit == null) {
                            MenuCard(
                                title = stringResource(R.string.gestures_outfits),
                                description = stringResource(R.string.gestures_outfits_desc),
                                onClick = { outfitsOpen = true },
                            )
                        } else {
                            OutfitGestureStatus(
                                hasOwn = outfit.gestures != null,
                                onUseGlobal = { wardrobeViewModel.setOutfitGestures(outfit.id, null) },
                            )
                        }
                        GestureSection.entries.forEach { entry ->
                            MenuCard(
                                title = stringResource(entry.title),
                                description = stringResource(entry.description),
                                onClick = { sectionName = entry.name },
                            )
                        }
                    } else {
                        when (section) {
                            GestureSection.Blink -> BlinkSection(editor, wardrobeViewModel)
                            GestureSection.Talk -> TalkSection(editor, wardrobeViewModel)
                            GestureSection.Kiss -> KissSection(editor, wardrobeViewModel)
                        }
                        TextButton(
                            onClick = { editor.reset(section.group) },
                            modifier = Modifier.align(Alignment.End),
                        ) { Text(stringResource(R.string.gestures_reset)) }
                    }
                }
            }
        }
    }
}

/** Cuadrícula de 2 columnas con los outfits: 4 a la vez (2x2) y el resto con scroll. */
@Composable
private fun OutfitGrid(
    wardrobe: WardrobeViewModel,
    onPick: (Outfit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val outfits = wardrobe.outfits
    if (outfits.isEmpty()) {
        Box(modifier = modifier.padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.gestures_outfits_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    BoxWithConstraints(modifier = modifier) {
        val padding = 16.dp
        val gap = 12.dp
        // Alto de cada celda para que quepan exactamente 2 filas en pantalla.
        val cellHeight = ((maxHeight - padding * 2 - gap) / 2).coerceAtLeast(120.dp)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(padding),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            items(outfits, key = { it.id }) { outfit ->
                OutfitGridCell(outfit, wardrobe, Modifier.height(cellHeight), onClick = { onPick(outfit) })
            }
        }
    }
}

@Composable
private fun OutfitGridCell(
    outfit: Outfit,
    wardrobe: WardrobeViewModel,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), shape)
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            OutfitPreviewImage(outfit, wardrobe, Modifier.fillMaxSize())
            if (outfit.gestures != null) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = stringResource(R.string.gestures_outfit_custom),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                        .padding(3.dp),
                )
            }
        }
        Text(
            text = outfit.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Aviso en la página de un outfit: si usa gestos propios o los globales, y cómo volver a los globales. */
@Composable
private fun OutfitGestureStatus(hasOwn: Boolean, onUseGlobal: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    if (hasOwn) R.string.gestures_outfit_own else R.string.gestures_outfit_global,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.weight(1f),
            )
            if (hasOwn) {
                TextButton(onClick = onUseGlobal) { Text(stringResource(R.string.gestures_outfit_use_global)) }
            }
        }
    }
}

@Composable
private fun MenuCard(title: String, description: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun BlinkSection(vm: GestureEditor, wardrobe: WardrobeViewModel) {
    val s = vm.settings
    AssetSelectorRow(R.string.gestures_eyes, R.string.gestures_pick_eyes, WardrobeSlot.Eyes, s.blinkEyes, wardrobe) { id ->
        vm.update { it.copy(blinkEyes = id) }
    }
    AssetSelectorRow(R.string.gestures_mouth, R.string.gestures_pick_mouth, WardrobeSlot.Mouth, s.blinkMouth, wardrobe) { id ->
        vm.update { it.copy(blinkMouth = id) }
    }

    SliderCard(
        label = stringResource(R.string.gestures_blink_every, "%.1f".format(s.blinkIntervalSec)),
        hint = stringResource(R.string.gestures_blink_every_hint),
        value = s.blinkIntervalSec,
        range = GestureSettings.INTERVAL_RANGE,
        steps = 15, // de 0.5 s en 0.5 s: 2, 2.5 ... 10
        onChange = { v -> vm.update { it.copy(blinkIntervalSec = (v * 2).roundToInt() / 2f) } },
    )
    SliderCard(
        label = stringResource(R.string.gestures_blink_for, "%.2f".format(s.blinkDurationSec)),
        hint = null,
        value = s.blinkDurationSec,
        range = GestureSettings.DURATION_RANGE,
        steps = 7, // de 0.05 s en 0.05 s: 0.10, 0.15 ... 0.50
        onChange = { v -> vm.update { it.copy(blinkDurationSec = (v * 20).roundToInt() / 20f) } },
    )
}

@Composable
private fun TalkSection(vm: GestureEditor, wardrobe: WardrobeViewModel) {
    val s = vm.settings
    AssetSelectorRow(R.string.gestures_eyes, R.string.gestures_pick_eyes, WardrobeSlot.Eyes, s.talkEyes, wardrobe) { id ->
        vm.update { it.copy(talkEyes = id) }
    }
    AssetSelectorRow(R.string.gestures_mouth_open, R.string.gestures_pick_mouth, WardrobeSlot.Mouth, s.talkMouth, wardrobe) { id ->
        vm.update { it.copy(talkMouth = id) }
    }
    Text(
        text = stringResource(R.string.gestures_talk_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun KissSection(vm: GestureEditor, wardrobe: WardrobeViewModel) {
    val s = vm.settings
    AssetSelectorRow(R.string.gestures_eyes, R.string.gestures_pick_eyes, WardrobeSlot.Eyes, s.kissEyes, wardrobe) { id ->
        vm.update { it.copy(kissEyes = id) }
    }
    AssetSelectorRow(R.string.gestures_mouth, R.string.gestures_pick_mouth, WardrobeSlot.Mouth, s.kissMouth, wardrobe) { id ->
        vm.update { it.copy(kissMouth = id) }
    }
}

/** Tarjeta con un slider; [hint] es una nota opcional debajo. */
@Composable
private fun SliderCard(
    label: String,
    hint: String?,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
            if (hint != null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
    }
}

/**
 * Fila "Ojos" / "Boca": muestra lo elegido (miniatura y nombre) y, al tocarla, abre una hoja inferior
 * con todos los assets de esa categoría (nativos e importados).
 * [selectedId] null = "Los del Armario" (no cambia el ojo o la boca que Nao lleva puesta).
 */
@Composable
private fun AssetSelectorRow(
    @StringRes label: Int,
    @StringRes pickerTitle: Int,
    slot: WardrobeSlot,
    selectedId: String?,
    wardrobe: WardrobeViewModel,
    onSelect: (String?) -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val items = wardrobe.itemsFor(slot)
    // Si el asset elegido ya no existe (un importado que se eliminó), se muestra como "Los del Armario":
    // es también lo que Nao usa en ese caso.
    val current = items.firstOrNull { it.fileName == selectedId }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { picking = true },
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(56.dp)) {
                if (current != null) {
                    AssetThumb(current, Modifier.fillMaxSize())
                } else {
                    Icon(
                        Icons.Default.Face,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.align(Alignment.Center).size(28.dp),
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    current?.label ?: stringResource(R.string.gestures_from_wardrobe),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }

    if (picking) {
        AssetPickerSheet(
            title = stringResource(pickerTitle),
            items = items,
            selectedId = current?.fileName,
            onSelect = { id ->
                onSelect(id)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

/** Hoja inferior con la cuadrícula de assets para elegir. El primer recuadro es "Los del Armario". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssetPickerSheet(
    title: String,
    items: List<WardrobeItem>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(96.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "wardrobe_default") {
                PickerCell(
                    label = stringResource(R.string.gestures_from_wardrobe),
                    selected = selectedId == null,
                    onClick = { onSelect(null) },
                ) {
                    Icon(
                        Icons.Default.Face,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.Center).size(32.dp),
                    )
                }
            }
            items(items, key = { it.fileName }) { asset ->
                PickerCell(
                    label = asset.label,
                    selected = asset.fileName == selectedId,
                    onClick = { onSelect(asset.fileName) },
                ) {
                    AssetThumb(asset, Modifier.fillMaxSize().padding(6.dp))
                }
            }
        }
    }
}

@Composable
private fun PickerCell(
    label: String,
    selected: Boolean,
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
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
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
        ) {
            content()
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(20.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)),
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
        }
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

/** Miniatura de un asset (la misma, recortada y con la cabeza de fondo, que usa el Armario). */
@Composable
private fun AssetThumb(item: WardrobeItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val thumbnail by produceState<ImageBitmap?>(
        initialValue = WardrobeBitmaps.cachedThumbnail(item),
        key1 = item.assetPath,
    ) {
        value = WardrobeBitmaps.thumbnail(context, item)
    }
    thumbnail?.let {
        Image(
            bitmap = it,
            contentDescription = item.label,
            modifier = modifier,
            contentScale = ContentScale.Fit,
        )
    }
}
