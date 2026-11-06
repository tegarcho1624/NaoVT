package com.naovt.app.dialog

/** Frases especiales de Nao cuando se guarda un recuerdo nuevo. */
object NaoMemoryPhrases {

    private val newMemory = listOf(
        "Un nuevo recuerdo para nuestra colección. Gracias por compartirlo conmigo.",
        "¡Qué bonito recuerdo! Lo voy a guardar con mucho cariño.",
        "Me encanta tener un recuerdo más contigo. Gracias por confiármelo.",
    )

    fun newMemory(): String = newMemory.random()
}
