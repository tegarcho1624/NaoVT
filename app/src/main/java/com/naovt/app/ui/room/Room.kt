package com.naovt.app.ui.room

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.naovt.app.R

/** Habitaciones disponibles. Para añadir una nueva, agrega una entrada aquí (imagen y nombre). */
enum class Room(@DrawableRes val drawable: Int, @StringRes val label: Int) {
    NaoRoom(R.drawable.nao_room, R.string.room_nao_room),
    SalaDeEstar(R.drawable.sala_de_estar, R.string.room_sala_de_estar);

    /** Siguiente habitación en orden circular. */
    fun next(): Room = entries[(ordinal + 1) % entries.size]
}
