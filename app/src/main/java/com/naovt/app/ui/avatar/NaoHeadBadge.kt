package com.naovt.app.ui.avatar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Zona de la cara dentro del lienzo de 1024x1536 de las capas (en píxeles del PNG).
private const val LAYER_WIDTH_PX = 1024f
private const val HEAD_LEFT_PX = 242f
private const val HEAD_TOP_PX = 0f
private const val HEAD_SIZE_PX = 540f

/**
 * Retrato circular de Nao (solo la cara), hecho recortando el mismo [NaoVTAvatar]
 * para que use exactamente las mismas capas y expresiones.
 */
@Composable
fun NaoHeadBadge(
    expression: NaoExpression,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    mouthOverride: NaoMouth? = null,
    isBlinking: Boolean = false,
) {
    val dpPerPx = size.value / HEAD_SIZE_PX

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        NaoVTAvatar(
            expression = expression,
            mouthOverride = mouthOverride,
            isBlinking = isBlinking,
            modifier = Modifier
                .wrapContentSize(align = Alignment.TopStart, unbounded = true)
                .requiredWidth((LAYER_WIDTH_PX * dpPerPx).dp)
                .offset(x = (-HEAD_LEFT_PX * dpPerPx).dp, y = (-HEAD_TOP_PX * dpPerPx).dp),
        )
    }
}
