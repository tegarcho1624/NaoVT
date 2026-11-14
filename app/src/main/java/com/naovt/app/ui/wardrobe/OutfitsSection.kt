package com.naovt.app.ui.wardrobe

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naovt.app.R
import com.naovt.app.wardrobe.Outfit
import com.naovt.app.wardrobe.WardrobeBitmaps
import com.naovt.app.wardrobe.WardrobeViewModel

private const val MAX_NAME_LENGTH = 30
private val CARD_WIDTH = 72.dp
private val CARD_PREVIEW_HEIGHT = 96.dp

/**
 * "Mis Outfits": el botón para guardar el atuendo actual y una fila horizontal con los conjuntos
 * guardados. Tocar uno viste a Nao con él; mantenerlo presionado ofrece eliminarlo.
 */
@Composable
fun OutfitsSection(viewModel: WardrobeViewModel, modifier: Modifier = Modifier) {
    var saving by rememberSaveable { mutableStateOf(false) }
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
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(outfits, key = { it.id }) { outfit ->
                    OutfitCard(
                        outfit = outfit,
                        viewModel = viewModel,
                        onApply = { viewModel.applyOutfit(outfit) },
                        onLongPress = { deleting = outfit },
                    )
                }
            }
        }
    }

    if (saving) {
        SaveOutfitDialog(
            defaultName = stringResource(R.string.wardrobe_outfit_default_name, outfits.size + 1),
            onDismiss = { saving = false },
            onSave = { name ->
                viewModel.saveOutfit(name)
                saving = false
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
    onApply: () -> Unit,
    onLongPress: () -> Unit,
) {
    val context = LocalContext.current
    // Si un asset del outfit ya no existe, la vista previa lo omite igual que al aplicarlo.
    val layerPaths = viewModel.resolveOutfit(outfit).layerPaths()
    val preview by produceState<ImageBitmap?>(
        initialValue = WardrobeBitmaps.cachedPreview(layerPaths),
        key1 = layerPaths,
    ) {
        value = WardrobeBitmaps.outfitPreview(context, layerPaths)
    }
    val shape = RoundedCornerShape(14.dp)

    Column(
        modifier = Modifier
            .width(CARD_WIDTH)
            .pointerInput(outfit.id) {
                detectTapGestures(onTap = { onApply() }, onLongPress = { onLongPress() })
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
            preview?.let {
                Image(
                    bitmap = it,
                    contentDescription = outfit.name,
                    modifier = Modifier.fillMaxSize().padding(2.dp),
                    contentScale = ContentScale.Fit,
                )
            }
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

/** Pregunta el nombre del conjunto. Solo permite guardar si el nombre no está vacío. */
@Composable
private fun SaveOutfitDialog(defaultName: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(defaultName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wardrobe_outfit_name_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_NAME_LENGTH) },
                singleLine = true,
                label = { Text(stringResource(R.string.wardrobe_outfit_name_hint)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.wardrobe_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.wardrobe_cancel)) } },
    )
}
