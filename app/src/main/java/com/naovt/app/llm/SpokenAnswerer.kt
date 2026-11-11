package com.naovt.app.llm

import android.content.Context
import com.naovt.app.R
import com.naovt.app.voice.VoicePreferences

/**
 * Mini chat de voz: recibe lo que escribe el usuario, se lo pasa al modelo local y entrega la
 * respuesta a la voz de Nao, oración por oración, sin mostrar el texto en pantalla.
 *
 * Lo usan la pantalla principal y el Modo Flotante; cada uno aporta cómo hablar ([speaker]) y cómo
 * mostrar el indicador de "pensando" ([onThinkingChanged]). Todo ocurre en el hilo principal.
 *
 * @param speaker dice una oración. [first] = true en la primera de la respuesta (corta lo que se estuviera diciendo).
 * @param onProblem recibe un mensaje listo para mostrarle al usuario cuando algo no se pudo.
 */
class SpokenAnswerer(
    context: Context,
    private val speaker: (text: String, first: Boolean) -> Unit,
    private val onThinkingChanged: (Boolean) -> Unit,
    private val onProblem: (String) -> Unit,
) {

    private val appContext = context.applicationContext
    private val repository = NaoLLMRepository.get(appContext)
    private val history = ArrayDeque<String>() // líneas "Usuario: ..." y "Nao: ..."
    private var session: Session? = null

    val isBusy: Boolean get() = session != null

    /**
     * Envía [rawText] al modelo. Devuelve true si se aceptó (la pantalla puede vaciar el campo de
     * texto); false si estaba vacío, Nao ya está pensando o su voz está silenciada.
     */
    fun ask(rawText: String): Boolean {
        val text = rawText.trim()
        if (text.isEmpty() || session != null) return false
        if (!VoicePreferences.isEnabled(appContext)) {
            // Con la voz silenciada no tiene sentido gastar batería generando una respuesta que no se oirá.
            onProblem(appContext.getString(R.string.mini_chat_muted))
            return false
        }

        val current = Session(text)
        session = current
        onThinkingChanged(true)
        repository.acquire() // mantiene el modelo cargado mientras se usa
        repository.loadModel { result ->
            if (current.finished) return@loadModel // se canceló mientras cargaba
            when (result) {
                LlmLoadResult.Ready -> current.generate()
                LlmLoadResult.ModelNotFound -> current.fail(appContext.getString(R.string.mini_chat_no_model))
                is LlmLoadResult.Failed -> current.fail(appContext.getString(R.string.mini_chat_load_failed))
            }
        }
        return true
    }

    /** Corta la respuesta en curso (si la hay). */
    fun cancel() {
        session?.cancel()
    }

    private fun historyBlock(): String =
        if (history.isEmpty()) "" else "Conversación reciente:\n" + history.joinToString("\n")

    private fun remember(userText: String, reply: String) {
        history.addLast("Usuario: $userText")
        if (reply.isNotBlank()) history.addLast("Nao: $reply")
        while (history.size > HISTORY_LINES) history.removeFirst()
    }

    private inner class Session(private val prompt: String) {

        private val processor = ReplyStreamProcessor()
        var finished = false
            private set
        private var spokeFirst = false

        fun generate() {
            repository.generarRespuesta(
                prompt = prompt,
                contexto = historyBlock(),
                onToken = { token ->
                    if (!finished) {
                        speak(processor.feed(token))
                        if (processor.stopRequested) {
                            // El modelo se puso a escribir lo que diría el usuario: se corta ahí.
                            repository.cancelar()
                            finish()
                        }
                    }
                },
                onDone = { if (!finished) finish() },
                onError = { if (!finished) fail(appContext.getString(R.string.mini_chat_error)) },
            )
        }

        fun finish() {
            finished = true
            speak(processor.finish())
            remember(prompt, processor.visibleText)
            end()
        }

        fun fail(message: String) {
            finished = true
            onProblem(message)
            end()
        }

        fun cancel() {
            if (finished) return
            finished = true
            repository.cancelar()
            end()
        }

        private fun speak(sentences: List<String>) {
            for (sentence in sentences) {
                val clean = SpeechText.clean(sentence)
                if (clean.isEmpty()) continue
                speaker(clean, !spokeFirst)
                spokeFirst = true
            }
        }

        private fun end() {
            repository.release()
            if (session === this) session = null
            onThinkingChanged(false)
        }
    }

    private companion object {
        const val HISTORY_LINES = 6
    }
}
