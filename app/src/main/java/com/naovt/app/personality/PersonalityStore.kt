package com.naovt.app.personality

import android.content.Context

/**
 * Personalidad de Nao y nombre del usuario, guardados en SharedPreferences (`naovt_personality`).
 * Los lee [com.naovt.app.llm.NaoLLMRepository] cada vez que genera una respuesta, así que un cambio
 * vale desde el siguiente mensaje (chat, mini chat y Nao flotante).
 */
object PersonalityStore {

    private const val PREFS_NAME = "naovt_personality"
    private const val K_USER_NAME = "user_name"
    private const val K_PERSONALITY = "personality"

    const val DEFAULT_PERSONALITY = "Eres Nao, una mascota virtual amigable. Responde de forma cariñosa."
    const val DEFAULT_USER_NAME = "usuario"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Lo escrito por el usuario (puede estar vacío). */
    fun userName(context: Context): String = prefs(context).getString(K_USER_NAME, "").orEmpty()

    fun personality(context: Context): String = prefs(context).getString(K_PERSONALITY, "").orEmpty()

    fun save(context: Context, userName: String, personality: String) {
        prefs(context).edit()
            .putString(K_USER_NAME, userName.trim())
            .putString(K_PERSONALITY, personality.trim())
            .apply()
    }

    /**
     * El System Prompt que se inyecta antes del mensaje del usuario:
     * "Instrucciones del sistema: [Personalidad]. El usuario se llama [Nombre]. Responde siempre en personaje."
     * Con campos vacíos: personalidad y nombre por defecto.
     */
    fun systemPrompt(context: Context): String =
        buildSystemPrompt(personality(context), userName(context))

    fun buildSystemPrompt(personality: String, userName: String): String {
        val text = personality.trim().ifEmpty { DEFAULT_PERSONALITY }.trimEnd('.', ' ', '\n')
        val name = userName.trim().ifEmpty { DEFAULT_USER_NAME }
        return "Instrucciones del sistema: $text. El usuario se llama $name. Responde siempre en personaje. " +
            // Nao habla en voz alta: nada de formato que suene mal.
            "Responde en español, con frases cortas y naturales, sin listas, sin emojis y sin formato especial."
    }
}
