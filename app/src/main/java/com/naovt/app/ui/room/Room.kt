package com.naovt.app.ui.room

import androidx.annotation.DrawableRes
import com.naovt.app.R

/** Habitaciones disponibles. Para añadir una nueva, solo agrega una entrada aquí. */
enum class Room(@DrawableRes val drawable: Int) {
    NaoRoom(R.drawable.nao_room),
    SalaDeEstar(R.drawable.sala_de_estar);

    /** Siguiente habitación en orden circular. */
    fun next(): Room = entries[(ordinal + 1) % entries.size]
}
