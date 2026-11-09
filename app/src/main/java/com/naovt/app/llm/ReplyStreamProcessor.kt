package com.naovt.app.llm

/**
 * Procesa la respuesta del modelo a medida que llega:
 * - quita una etiqueta inicial "Nao:" si el modelo la escribe;
 * - detecta si el modelo empieza a inventar el turno del usuario ("Usuario: ...") y corta ahí;
 * - separa el texto en oraciones listas para decir en voz alta.
 */
class ReplyStreamProcessor {

    private val raw = StringBuilder()
    private val chunker = SentenceChunker()
    private var headDecided = false
    private var emitted = 0

    /** Texto de la respuesta tal como debe mostrarse en el chat. */
    var visibleText: String = ""
        private set

    /** true si hay que cortar la generación (el modelo se puso a escribir el turno del usuario). */
    var stopRequested: Boolean = false
        private set

    /** Agrega un fragmento y devuelve las oraciones que ya se pueden decir. */
    fun feed(token: String): List<String> {
        if (stopRequested) return emptyList()
        raw.append(token)
        return process(isEnd = false)
    }

    /** Termina la respuesta y devuelve lo que falte por decir. */
    fun finish(): List<String> {
        val sentences = ArrayList(process(isEnd = true))
        chunker.flush()?.let { sentences.add(it) }
        return sentences
    }

    private fun process(isEnd: Boolean): List<String> {
        var text = raw.toString()

        if (!headDecided) {
            val label = LEADING_LABEL.find(text)
            when {
                label != null -> {
                    text = text.removeRange(label.range)
                    raw.setLength(0)
                    raw.append(text)
                    headDecided = true
                }
                // Con unos pocos caracteres ya se sabe que no empieza con "Nao:".
                text.trimStart().length >= PROBE_LENGTH || isEnd -> headDecided = true
                else -> return emptyList() // aún no se sabe
            }
        }

        val echo = ROLE_ECHO.find(text)
        if (echo != null) {
            text = text.substring(0, echo.range.first)
            stopRequested = true
        }

        visibleText = text.trimStart().let { if (isEnd || stopRequested) it.trimEnd() else it }
        if (visibleText.length <= emitted) return emptyList()
        val fresh = visibleText.substring(emitted)
        emitted = visibleText.length
        return chunker.feed(fresh)
    }

    private companion object {
        const val PROBE_LENGTH = 5
        val LEADING_LABEL = Regex("^\\s*Nao\\s*:\\s*")
        val ROLE_ECHO = Regex("\\n\\s*(Usuario|User|Humano|Tú)\\s*:")
    }
}
