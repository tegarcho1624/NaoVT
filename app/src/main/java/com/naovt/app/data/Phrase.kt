package com.naovt.app.data

/** Categoría de una frase. Por ahora Nao elige al tocarla solo entre las "Normal". */
enum class PhraseCategory { Normal, Consuelo, Meme }

/** Una frase que Nao puede decir. */
data class Phrase(
    val id: Int,
    val text: String,
    val category: PhraseCategory,
)
