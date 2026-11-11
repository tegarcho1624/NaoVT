package com.naovt.app.data

import androidx.annotation.DrawableRes

enum class TipoHabitacion { IMAGEN, VIDEO }

/**
 * Una habitación (fondo) de Nao.
 *
 * - Las predeterminadas ([esPredeterminada] = true) vienen con la app: usan una imagen de los
 *   recursos ([recurso]) y no se pueden eliminar. Su [rutaArchivo] solo es informativa ("res:nombre").
 * - Las personalizadas apuntan a una copia del archivo elegido en filesDir/habitaciones/.
 */
data class Habitacion(
    val id: String,
    val nombre: String,
    val rutaArchivo: String,
    val esPredeterminada: Boolean,
    val tipo: TipoHabitacion,
    @DrawableRes val recurso: Int = 0,
)
