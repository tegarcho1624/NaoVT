package com.naovt.app.ui.alerts

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.naovt.app.R
import com.naovt.app.ui.avatar.NaoExpression

/** Expresiones que se pueden elegir en una alerta, en el orden del menú. */
val SelectableExpressions = listOf(
    NaoExpression.Neutral,
    NaoExpression.Angry,
    NaoExpression.Loved,
    NaoExpression.Yandere,
)

@StringRes
fun NaoExpression.labelRes(): Int = when (this) {
    NaoExpression.Neutral -> R.string.expr_normal
    NaoExpression.Angry -> R.string.expr_angry
    NaoExpression.Loved -> R.string.expr_love
    NaoExpression.Yandere -> R.string.expr_yandere
}

/** Nombre para el menú; avisa si la expresión todavía usa la cara normal (faltan sus imágenes). */
@Composable
fun NaoExpression.displayName(): String {
    val name = stringResource(labelRes())
    return if (provisional) stringResource(R.string.alerts_expression_pending, name) else name
}
