package com.naovt.app.gestures

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.naovt.app.wardrobe.WardrobeViewModel

/**
 * Los gestos que Nao debe usar ahora mismo: los propios del outfit que lleva puesto si ese outfit
 * tiene gestos guardados; si no (o si Nao no lleva un outfit guardado), los globales del Menú de Gestos.
 * Se recalcula solo al cambiar de outfit o al editar los gestos.
 */
@Composable
fun rememberEffectiveGestures(): CharacterGestures {
    val global: GesturesViewModel = viewModel()
    val wardrobe: WardrobeViewModel = viewModel()
    return wardrobe.activeOutfit()?.gestures ?: global.settings
}
