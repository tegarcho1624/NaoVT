package com.naovt.app.llm

/**
 * Junta el texto que llega token por token y va entregando oraciones completas,
 * para que Nao pueda empezar a hablar sin esperar a que termine toda la respuesta.
 *
 * Una oración termina en . ! ? … seguido de un espacio o salto de línea (así "3.5" no se corta),
 * o en un salto de línea. Las oraciones muy cortas se juntan con la siguiente.
 */
class SentenceChunker(private val minLength: Int = 12) {

    private val buffer = StringBuilder()

    /** Agrega texto y devuelve las oraciones que ya están completas. */
    fun feed(text: String): List<String> {
        buffer.append(text)
        val sentences = ArrayList<String>()
        while (true) {
            val end = findSentenceEnd() ?: break
            val sentence = buffer.substring(0, end).trim()
            buffer.delete(0, end)
            if (sentence.isNotEmpty()) sentences.add(sentence)
        }
        return sentences
    }

    /** Lo que quede sin decir al terminar la respuesta. */
    fun flush(): String? {
        val rest = buffer.toString().trim()
        buffer.clear()
        return rest.ifEmpty { null }
    }

    /** Posición (exclusiva) donde termina la primera oración completa, o null si aún no hay. */
    private fun findSentenceEnd(): Int? {
        for (i in buffer.indices) {
            val c = buffer[i]
            val endsWithPunctuation = c in TERMINATORS && i + 1 < buffer.length && buffer[i + 1].isWhitespace()
            val endsWithNewline = c == '\n' && buffer.substring(0, i).isNotBlank()
            if ((endsWithPunctuation || endsWithNewline) && i + 1 >= minLength) return i + 1
        }
        return null
    }

    private companion object {
        const val TERMINATORS = ".!?…"
    }
}
