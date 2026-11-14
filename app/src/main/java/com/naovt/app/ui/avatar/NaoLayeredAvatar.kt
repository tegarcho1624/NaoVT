package com.naovt.app.ui.avatar

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import com.naovt.app.R
import com.naovt.app.gestures.GestureSettings
import com.naovt.app.wardrobe.CharacterConfig
import com.naovt.app.wardrobe.LAYER_HEIGHT
import com.naovt.app.wardrobe.LAYER_WIDTH
import com.naovt.app.wardrobe.WardrobeBitmaps
import com.naovt.app.wardrobe.WardrobeSlot

/**
 * Nao hecha de las capas del [config] (su [CharacterConfig.order] define el orden de dibujo), apiladas
 * dentro de un mismo lienzo de 1024x1536. El fondo se dibuja afuera, siempre debajo.
 *
 * La cara sigue las mismas prioridades que [NaoVTAvatar] (de mayor a menor), con los ojos y bocas
 * que se eligen en el Menú de Gestos ([gestures]):
 * 1. Expresión especial (cariño): ojos y boca del gesto "Beso".
 * 2. Hablando: ojos del gesto "Hablar"; la boca alterna con [mouthOverride] (boca abierta de "Hablar").
 * 3. Parpadeo: ojos (y boca) del gesto "Pestañeo" un instante.
 * 4. Los ojos y la boca elegidos en el Armario.
 *
 * Las expresiones y el habla solo reemplazan ojos y boca; la ropa y el cabello no cambian.
 */
@Composable
fun NaoLayeredAvatar(
    config: CharacterConfig,
    expression: NaoExpression,
    modifier: Modifier = Modifier,
    mouthOverride: NaoMouth? = null,
    isSpeaking: Boolean = false,
    isBlinking: Boolean = false,
    gestures: GestureSettings = GestureSettings(),
) {
    // Los gestos (Menú de Gestos) eligen ojos y boca; si no cambian nada (null) se usan los del Armario.
    val wardrobeEyes = config.fileFor(WardrobeSlot.Eyes)
    val wardrobeMouth = config.fileFor(WardrobeSlot.Mouth)
    val eyesFile: String? = when {
        expression == NaoExpression.Loved -> gestures.kissEyes ?: wardrobeEyes
        // Angry y Yandere aún no tienen ojos propios: usan los elegidos.
        isSpeaking -> gestures.talkEyes ?: wardrobeEyes
        isBlinking -> gestures.blinkEyes ?: wardrobeEyes
        else -> wardrobeEyes
    }
    val mouthFile: String? = when {
        mouthOverride == NaoMouth.Happy -> gestures.talkMouth ?: wardrobeMouth
        mouthOverride == NaoMouth.Open -> gestures.kissMouth ?: wardrobeMouth
        mouthOverride == NaoMouth.Closed -> wardrobeMouth
        expression == NaoExpression.Loved -> gestures.kissMouth ?: wardrobeMouth
        isBlinking -> gestures.blinkMouth ?: wardrobeMouth
        else -> wardrobeMouth
    }
    val shown = config
        .with(WardrobeSlot.Eyes, eyesFile)
        .with(WardrobeSlot.Mouth, mouthFile)

    BoxWithConstraints(modifier = modifier.aspectRatio(LAYER_WIDTH.toFloat() / LAYER_HEIGHT)) {
        val heightPx = with(LocalDensity.current) { maxHeight.roundToPx() }
        val sampleSize = remember(heightPx) { WardrobeBitmaps.sampleSizeFor(heightPx) }
        val description = stringResource(R.string.nao_description)

        for (slot in shown.order) {
            key(slot) {
                AvatarLayer(
                    path = shown.assetPathFor(slot),
                    // Si el asset de un gesto ya no existe (un importado que se eliminó), se usa el del Armario.
                    fallbackPath = config.assetPathFor(slot),
                    sampleSize = sampleSize,
                    // La descripción para accesibilidad va solo en el cuerpo.
                    description = if (slot == WardrobeSlot.Body) description else null,
                )
            }
        }
    }
}

/**
 * Una capa. Si [path] cambia, la imagen anterior se queda visible hasta que la nueva está lista,
 * para que cambiar de ojos, boca o ropa no deje a Nao parpadeando con huecos.
 */
@Composable
private fun AvatarLayer(path: String?, fallbackPath: String?, sampleSize: Int, description: String?) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<ImageBitmap?>(path?.let { WardrobeBitmaps.cachedLayer(it, sampleSize) }) }

    LaunchedEffect(path, fallbackPath, sampleSize) {
        bitmap = when {
            path == null -> null
            else -> WardrobeBitmaps.cachedLayer(path, sampleSize)
                ?: WardrobeBitmaps.layer(context, path, sampleSize)
                ?: fallbackPath?.takeIf { it != path }?.let { WardrobeBitmaps.layer(context, it, sampleSize) }
                ?: bitmap
        }
    }

    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = description,
            modifier = Modifier.fillMaxSize(),
            // Las capas miden 1024x1536 (algunas, 1024x1535): se ajustan siempre al lienzo exacto
            // para que encajen entre sí.
            contentScale = ContentScale.FillBounds,
        )
    }
}
