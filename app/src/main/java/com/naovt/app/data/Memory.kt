package com.naovt.app.data

/** Un recuerdo guardado en el Baúl. [imagePath] apunta a la copia interna (filesDir/recuerdos/). */
data class Memory(
    val id: String,
    val imagePath: String,
    val note: String,
    val createdAt: Long,
)
