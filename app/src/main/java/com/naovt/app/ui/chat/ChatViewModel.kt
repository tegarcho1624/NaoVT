package com.naovt.app.ui.chat

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.naovt.app.R
import com.naovt.app.llm.LlmLoadResult
import com.naovt.app.llm.ModelLocator
import com.naovt.app.llm.NaoLLMRepository
import com.naovt.app.llm.NotGgufException
import com.naovt.app.llm.ReplyStreamProcessor
import com.naovt.app.llm.SpeechText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatMessage(val id: Long, val fromUser: Boolean, val text: String)

sealed interface ChatModelState {
    data object Idle : ChatModelState
    data object Loading : ChatModelState
    data object Importing : ChatModelState
    data object Ready : ChatModelState
    data object NotFound : ChatModelState
    data class Failed(val message: String) : ChatModelState
}

/** Lo que usa el chat para que Nao hable (lo conecta la pantalla con su NaoVoice). */
interface ChatSpeaker {
    /** [first] = primera oración de la respuesta: corta lo que estuviera diciendo. */
    fun speak(text: String, first: Boolean)
    fun stop()
}

/**
 * Lógica del chat con Nao: carga el modelo, manda lo que escribe el usuario y va mostrando la
 * respuesta token por token. Cada oración completa se entrega a [speaker] en cuanto está lista,
 * para que Nao empiece a hablar antes de que termine de "escribir".
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val repository = NaoLLMRepository.get(application)

    val messages = mutableStateListOf<ChatMessage>()

    var modelState by mutableStateOf<ChatModelState>(ChatModelState.Idle)
        private set

    var isGenerating by mutableStateOf(false)
        private set

    var importError by mutableStateOf<String?>(null)
        private set

    var speaker: ChatSpeaker? = null

    private var nextId = 0L
    private var currentReply: ReplySession? = null
    private var holdsModel = false // el chat mantiene el modelo cargado mientras está abierto

    /** Carga el modelo la primera vez que se abre el chat. */
    fun ensureModelLoaded() {
        when (modelState) {
            ChatModelState.Idle, ChatModelState.NotFound, is ChatModelState.Failed -> loadModel()
            else -> Unit
        }
    }

    fun retryLoad() = loadModel()

    fun clearImportError() {
        importError = null
    }

    /** Copia un modelo .gguf elegido por el usuario y lo carga. */
    fun importModel(uri: Uri) {
        if (isGenerating) return
        modelState = ChatModelState.Importing
        importError = null
        repository.unload()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { ModelLocator.import(app, uri) }
            result.onFailure { error ->
                Log.w(TAG, "No se pudo importar el modelo", error)
                importError = app.getString(
                    if (error is NotGgufException) R.string.chat_import_not_gguf else R.string.chat_import_failed
                )
            }
            // Si falló, vuelve a cargar el modelo que ya hubiera.
            loadModel()
        }
    }

    fun send(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || isGenerating || modelState != ChatModelState.Ready) return

        val history = buildHistory()
        messages += ChatMessage(nextId++, fromUser = true, text = text)
        val replyIndex = messages.size
        messages += ChatMessage(nextId++, fromUser = false, text = "")
        isGenerating = true

        val session = ReplySession(replyIndex)
        currentReply = session
        repository.generarRespuesta(
            prompt = text,
            contexto = history,
            onToken = { session.onToken(it) },
            onDone = { session.onDone() },
            onError = { session.onError(it) },
        )
    }

    /** Corta la respuesta que se esté generando y calla a Nao. */
    fun stopGeneration() {
        currentReply?.cancel()
        speaker?.stop()
    }

    /** Borra la conversación y empieza de cero. */
    fun newConversation() {
        stopGeneration()
        messages.clear()
    }

    override fun onCleared() {
        currentReply?.cancel()
        // El modelo lo comparten los mini chats: se suelta, y si nadie más lo usa se descarga solo.
        if (holdsModel) {
            holdsModel = false
            repository.release()
        }
        super.onCleared()
    }

    private fun loadModel() {
        if (!holdsModel) {
            repository.acquire()
            holdsModel = true
        }
        modelState = ChatModelState.Loading
        repository.loadModel { result ->
            modelState = when (result) {
                LlmLoadResult.Ready -> ChatModelState.Ready
                LlmLoadResult.ModelNotFound -> ChatModelState.NotFound
                is LlmLoadResult.Failed -> ChatModelState.Failed(result.message)
            }
        }
    }

    /** Las últimas intervenciones, para que Nao recuerde de qué se está hablando. */
    private fun buildHistory(): String {
        val recent = messages.filter { it.text.isNotBlank() }.takeLast(HISTORY_MESSAGES)
        if (recent.isEmpty()) return ""
        return "Conversación reciente:\n" + recent.joinToString("\n") { message ->
            (if (message.fromUser) "Usuario: " else "Nao: ") + message.text
        }
    }

    /** Una respuesta en curso: va armando el texto y entregando oraciones a la voz. */
    private inner class ReplySession(private val index: Int) {

        private val processor = ReplyStreamProcessor()
        private var finished = false
        private var spokeFirst = false

        fun onToken(token: String) {
            if (finished) return
            speak(processor.feed(token))
            showText()
            if (processor.stopRequested) {
                // El modelo se puso a escribir lo que diría el usuario: se corta ahí.
                repository.cancelar()
                finish()
            }
        }

        fun onDone() {
            if (!finished) finish()
        }

        fun onError(message: String) {
            if (finished) return
            Log.w(TAG, "Error del modelo: $message")
            finished = true
            isGenerating = false
            currentReply = null
            val partial = processor.visibleText
            update(if (partial.isBlank()) app.getString(R.string.chat_reply_error) else partial)
        }

        fun cancel() {
            if (finished) return
            repository.cancelar()
            finish(speakRest = false)
        }

        private fun finish(speakRest: Boolean = true) {
            finished = true
            val rest = processor.finish()
            if (speakRest) speak(rest)
            showText()
            if (processor.visibleText.isBlank()) update(app.getString(R.string.chat_reply_empty))
            isGenerating = false
            if (currentReply === this) currentReply = null
        }

        private fun speak(sentences: List<String>) {
            for (sentence in sentences) {
                val clean = SpeechText.clean(sentence)
                if (clean.isEmpty()) continue
                speaker?.speak(clean, first = !spokeFirst)
                spokeFirst = true
            }
        }

        private fun showText() = update(processor.visibleText)

        private fun update(text: String) {
            if (index < messages.size) messages[index] = messages[index].copy(text = text)
        }
    }

    private companion object {
        const val TAG = "ChatViewModel"
        const val HISTORY_MESSAGES = 6
    }
}
