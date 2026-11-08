package com.naovt.app.data

import com.naovt.app.ui.avatar.NaoExpression

/** Una alerta programada. [id] también se usa para identificar su alarma en el sistema. */
data class Alert(
    val id: Int,
    val text: String,
    val triggerAtMillis: Long,
    val expression: NaoExpression,
    val fired: Boolean = false,
)
