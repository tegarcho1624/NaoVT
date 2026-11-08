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

/**
 * Bocas disponibles.
 * - [Open] es la boca de beso ("3"), parte de la cara de cariño.
 * - [Happy] es la boca abierta feliz, que se alterna con [Closed] al hablar.
 */
enum class NaoMouth(@DrawableRes val drawable: Int) {
    Closed(R.drawable.boca_base),
    Open(R.drawable.boca_besando),
    Happy(R.drawable.boca_abierta_feliz),
}

/**
 * Expresión facial de Nao: define qué capas de ojos y boca se muestran.
 * [provisional] = todavía no tiene sus imágenes y usa temporalmente la cara normal.
 * Cuando existan, solo hay que cambiar sus dos drawables aquí y quitar `provisional`.
 */
enum class NaoExpression(
    @DrawableRes val eyes: Int,
    val mouth: NaoMouth,
    val provisional: Boolean = false,
) {
    Neutral(R.drawable.ojos_base, NaoMouth.Closed),
    Loved(R.drawable.ojos_corazon_mejilla, NaoMouth.Open),
    Angry(R.drawable.ojos_base, NaoMouth.Closed, provisional = true), // Enojada
    Yandere(R.drawable.ojos_base, NaoMouth.Closed, provisional = true),
}

/** Relación de aspecto de todas las capas del personaje (1024x1536). */
private const val LAYER_ASPECT_RATIO = 1024f / 1536f

/**
 * Avatar de Nao hecho de capas PNG superpuestas dentro de un [Box].
 * Orden (de abajo hacia arriba): cuerpo -> brazo izq. -> brazo der. -> pecho -> ojos -> boca.
 * El fondo se dibuja afuera (ver RoomBackground), siempre debajo de este componente.
 *
 * Estados de la cara (de mayor a menor prioridad):
 * 1. Expresión especial ([expression] distinta de Neutral): cariño (ojos con corazón y boca de
 *    beso), o la de una alerta (enojada, yandere...).
 * 2. Hablando ([isSpeaking]): ojos normales abiertos; la boca la anima [mouthOverride].
 * 3. Parpadeo ([isBlinking]): ojos cerrados un instante.
 * 4. Base: ojos y boca base.
 *
 * [mouthOverride] permite forzar la boca (por ejemplo, al hablar).
 * Con [isSpeaking] e [isBlinking] en false (valores por defecto) los ojos son los de [expression].
 *
 * Los dos brazos son dibujos distintos (no son espejo exacto), por eso se usan dos imágenes.
 */
@Composable
fun NaoVTAvatar(
    expression: NaoExpression,
    modifier: Modifier = Modifier,
    mouthOverride: NaoMouth? = null,
    isSpeaking: Boolean = false,
    isBlinking: Boolean = false,
) {
    val eyes = when {
        expression != NaoExpression.Neutral -> expression.eyes
        isSpeaking -> NaoExpression.Neutral.eyes
        isBlinking -> R.drawable.ojos_feliz_cerrando
        else -> expression.eyes
    }
    Box(modifier = modifier.aspectRatio(LAYER_ASPECT_RATIO)) {
        AvatarLayer(R.drawable.nao_base, stringResource(R.string.nao_description))
        AvatarLayer(R.drawable.nao_brazo_izquierdo_base)
        AvatarLayer(R.drawable.nao_brazo_derecho_base)
        AvatarLayer(R.drawable.pecho_base)
        AvatarLayer(eyes)
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
