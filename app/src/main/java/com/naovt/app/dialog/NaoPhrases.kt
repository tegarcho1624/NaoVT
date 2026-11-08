package com.naovt.app.dialog

/** Frases que Nao puede decir. Para añadir o cambiar frases, edita solo esta lista. */
object NaoPhrases {

    private val all = listOf(
        "¡Hola! Me alegra mucho verte.",
        "¿Ya te cepillaste los dientes hoy?",
        "Me gusta mucho cuando sonríes.",
        "Recuerda tomar agua, es importante.",
        "¿Qué vamos a hacer hoy?",
        "No olvides descansar, te lo mereces.",
        // Extras
        "Estoy aquí contigo, siempre.",
        "¿Cómo te sientes hoy?",
        "Eres increíble, ¿lo sabías?",
        "Respira hondo, todo va a salir bien.",
        "¿Ya comiste algo rico?",
        "Me encanta pasar tiempo contigo.",
    )

    /** Frases originales: se cargan en el gestor de frases la primera vez que se abre la app. */
    val defaults: List<String> get() = all

    /** Frase al azar, evitando repetir la [previous] para que no suene monótono. */
    fun random(previous: String? = null): String {
        val candidates = if (all.size > 1) all.filter { it != previous } else all
        return candidates.random()
    }
}
