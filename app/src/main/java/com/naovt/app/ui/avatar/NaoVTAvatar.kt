package com.naovt.app.ui.avatar

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.naovt.app.R

/** Bocas disponibles. [Open] usa por ahora la boca de beso ("3"), que sirve para simular hablar. */
enum class NaoMouth(@DrawableRes val drawable: Int) {
    Closed(R.drawable.boca_base),
    Open(R.drawable.boca_besando),
}

/** Expresión facial de Nao: define qué capas de ojos y boca se muestran. */
enum class NaoExpression(@DrawableRes val eyes: Int, val mouth: NaoMouth) {
    Neutral(R.drawable.ojos_base, NaoMouth.Closed),
    Loved(R.drawable.ojos_corazon_mejilla, NaoMouth.Open),
}

/** Relación de aspecto de todas las capas del personaje (1024x1536). */
private const val LAYER_ASPECT_RATIO = 1024f / 1536f

/**
 * Avatar de Nao hecho de capas PNG superpuestas dentro de un [Box].
 * Orden (de abajo hacia arriba): cuerpo -> brazo izq. -> brazo der. -> pecho -> ojos -> boca.
 * El fondo se dibuja afuera (ver RoomBackground), siempre debajo de este componente.
 *
 * [mouthOverride] permite forzar la boca (por ejemplo, al hablar) sin cambiar los ojos.
 *
 * Los dos brazos son dibujos distintos (no son espejo exacto), por eso se usan dos imágenes.
 */
@Composable
fun NaoVTAvatar(
    expression: NaoExpression,
    modifier: Modifier = Modifier,
    mouthOverride: NaoMouth? = null,
) {
    Box(modifier = modifier.aspectRatio(LAYER_ASPECT_RATIO)) {
        AvatarLayer(R.drawable.nao_base, stringResource(R.string.nao_description))
        AvatarLayer(R.drawable.nao_brazo_izquierdo_base)
        AvatarLayer(R.drawable.nao_brazo_derecho_base)
        AvatarLayer(R.drawable.pecho_base)
        AvatarLayer(expression.eyes)
        AvatarLayer((mouthOverride ?: expression.mouth).drawable)
    }
}

/** Una capa: todas comparten el mismo lienzo, así que encajan al superponerse. */
@Composable
private fun AvatarLayer(@DrawableRes resId: Int, description: String? = null) {
    Image(
        painter = painterResource(resId),
        contentDescription = description,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Fit,
    )
}
