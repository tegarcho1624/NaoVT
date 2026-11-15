package com.naovt.app.ui.chat

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * Almacenamiento (interno) del historial del chat con Nao, guardado en SharedPreferences como JSON (`naovt_chat_history`).
 * Cada mensaje: `id`, `role` ("user" o "assistant"), `content` y `timestamp` (milisegundos).
 *
 * Leer es rápido y se hace una vez al abrir el chat. Guardar se hace en un hilo aparte, de uno en
 * uno y en orden, para no trabar la pantalla. Se conservan como máximo [MAX_MESSAGES] mensajes
 * (los más recientes).
 */
class ChatHistoryStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<ChatMessage> {
        val json = prefs.getString(KEY_MESSAGES, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val content = item.optString("content", "")
                val role = item.optString("role", "")
                if (content.isBlank() || (role != ROLE_USER && role != ROLE_ASSISTANT)) return@mapNotNull null
                ChatMessage(
                    id = item.optLong("id", index.toLong()),
                    fromUser = role == ROLE_USER,
                    text = content,
                    timestamp = item.optLong("timestamp", 0L),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Guarda [messages] (la lista completa) sin bloquear a quien llama. */
    fun saveAsync(messages: List<ChatMessage>) {
        val snapshot = messages.takeLast(MAX_MESSAGES)
        writer.execute {
            val array = JSONArray()
            snapshot.forEach { message ->
                array.put(
                    JSONObject()
                        .put("id", message.id)
                        .put("role", if (message.fromUser) ROLE_USER else ROLE_ASSISTANT)
                        .put("content", message.text)
                        .put("timestamp", message.timestamp),
                )
            }
            prefs.edit().putString(KEY_MESSAGES, array.toString()).apply()
        }
    }

    fun clearAsync() = saveAsync(emptyList())

    private companion object {
        const val PREFS_NAME = "naovt_chat_history"
        const val KEY_MESSAGES = "messages"
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val MAX_MESSAGES = 400

        // Un solo hilo para todas las escrituras: llegan en orden y nunca se pisan.
        val writer = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "nao-chat-history").apply { isDaemon = true }
        }
    }
}

/**
 * El historial de la conversación, UNO solo para toda la app: lo comparten el chat "Hablar con Nao"
 * y el mini chat de la pantalla principal (Nao flotante no lo usa). La lista vive en memoria como
 * fuente única, así que lo que escribe uno aparece en el otro al instante; cada cambio se guarda
 * con [ChatHistoryStore]. Se usa solo desde el hilo principal.
 */
object ChatHistory {

    private const val CONTEXT_MESSAGES = 10 // 5 interacciones (usuario + Nao)
    private const val CONTEXT_MESSAGE_CHARS = 400 // recorta mensajes largos para no saturar el contexto

    val messages = mutableStateListOf<ChatMessage>()

    private var store: ChatHistoryStore? = null
    private var nextId = 0L

    /** Lee lo guardado la primera vez; las siguientes no hace nada. */
    fun init(context: Context) {
        if (store != null) return
        val newStore = ChatHistoryStore(context)
        messages.addAll(newStore.load())
        nextId = (messages.maxOfOrNull { it.id } ?: -1L) + 1
        store = newStore
    }

    fun newId(): Long = nextId++

    /** Añade un mensaje al final y devuelve su posición. */
    fun add(fromUser: Boolean, text: String): Int {
        messages += ChatMessage(newId(), fromUser = fromUser, text = text)
        return messages.size - 1
    }

    /** Guarda los mensajes terminados (sin la respuesta en curso ni los avisos de la app). */
    fun persist() {
        store?.saveAsync(messages.filter { !it.transient && it.text.isNotBlank() })
    }

    fun clear() {
        messages.clear()
        store?.clearAsync()
    }

    /** Bloque de contexto con las últimas intervenciones, o "" si no hay. */
    fun recentContext(): String {
        val recent = messages.filter { it.text.isNotBlank() && !it.transient }.takeLast(CONTEXT_MESSAGES)
        if (recent.isEmpty()) return ""
        return "Conversación reciente:\n" + recent.joinToString("\n") { message ->
            (if (message.fromUser) "Usuario: " else "Nao: ") + message.text.take(CONTEXT_MESSAGE_CHARS)
        }
    }
}
