package com.naovt.app.alerts

import com.naovt.app.ui.avatar.NaoExpression

/** Lo que la pantalla principal muestra al abrir la app desde la notificación de una alerta. */
data class AlertDisplay(val expression: NaoExpression, val text: String)
