package com.naovt.app.llm

/** Limpia el texto para leerlo en voz alta (sin asteriscos, almohadillas, emojis...). */
object SpeechText {

    private val MARKDOWN = Regex("[*_#`~>|]")
    private val SYMBOLS = Regex("[\\p{So}\\p{Cs}\\uFE0F\\u200D]")
    private val SPACES = Regex("\\s+")

    fun clean(text: String): String =
        text.replace(MARKDOWN, " ")
            .replace(SYMBOLS, " ")
            .replace(SPACES, " ")
            .trim()
}
