package com.naovt.app.dialog

/** Frases especiales de Nao cuando se guarda un recuerdo nuevo. */
object NaoMemoryPhrases {

    private val newMemory = listOf(
        "Un nuevo recuerdo para nuestra colección. Gracias por compartirlo conmigo.",
        "¡Qué bonito recuerdo! Lo voy a guardar con mucho cariño.",
        "Me encanta tener un recuerdo más contigo. Gracias por confiármelo.",
    )

    /** Las mismas frases, para cargarlas en el gestor de frases la primera vez que se abre la app. */
    val defaults: List<String> get() = newMemory

    fun newMemory(): String = newMemory.random()
}
