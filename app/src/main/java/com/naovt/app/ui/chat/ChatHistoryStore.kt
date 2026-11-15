package com.naovt.app.ui.chat

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * Historial del chat con Nao guardado en SharedPreferences como JSON (`naovt_chat_history`).
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
