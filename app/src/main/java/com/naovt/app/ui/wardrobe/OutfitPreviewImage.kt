package com.naovt.app.ui.wardrobe

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.naovt.app.wardrobe.Outfit
import com.naovt.app.wardrobe.OutfitPreviews
import com.naovt.app.wardrobe.WardrobeBitmaps
import com.naovt.app.wardrobe.WardrobeViewModel

/**
 * La imagen estática de un outfit (`filesDir/previews/<id>.png`), usada en la fila "Mis Outfits" y en
 * la pantalla de Gestos de Conjuntos. Mientras esa imagen no existe todavía (se está generando),
 * muestra una versión reducida armada con las capas.
 */
@Composable
fun OutfitPreviewImage(outfit: Outfit, viewModel: WardrobeViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(
        initialValue = OutfitPreviews.cached(outfit.id),
        outfit.id,
        viewModel.previewVersion,
    ) {
        value = OutfitPreviews.load(context, outfit.id)
            ?: WardrobeBitmaps.outfitPreview(context, viewModel.resolveOutfit(outfit).layerPaths())
    }
    image?.let {
        Image(
            bitmap = it,
            contentDescription = outfit.name,
            modifier = modifier,
            contentScale = ContentScale.Fit,
        )
    }
}
