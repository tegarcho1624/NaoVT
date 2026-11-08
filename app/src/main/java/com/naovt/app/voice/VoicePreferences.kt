package com.naovt.app.voice

import android.content.Context

/**
 * Ajustes de la voz de Nao, guardados en SharedPreferences y compartidos por toda la app:
 * interruptor de silencio, volumen, tono y velocidad. [NaoVoice] los lee cada vez que habla.
 */
object VoicePreferences {

    /** Volumen relativo al volumen multimedia del teléfono (0.0 = mudo, 1.0 = completo). */
    const val DEFAULT_VOLUME = 1.0f
    const val MIN_VOLUME = 0.0f
    const val MAX_VOLUME = 1.0f

    /** 1.0 = normal. Un poco más agudo para sonar más tierna. */
    const val DEFAULT_PITCH = 1.15f
    const val MIN_PITCH = 0.5f
    const val MAX_PITCH = 2.0f

    /** 1.0 = normal. Ligeramente más lento para sonar cálida y no atropellada. */
    const val DEFAULT_SPEECH_RATE = 0.95f
    const val MIN_SPEECH_RATE = 0.5f
    const val MAX_SPEECH_RATE = 2.0f

    private const val PREFS_NAME = "naovt_settings"
    private const val KEY_VOICE_ENABLED = "voice_enabled"
    private const val KEY_VOLUME = "voice_volume"
    private const val KEY_PITCH = "voice_pitch"
    private const val KEY_SPEECH_RATE = "voice_speech_rate"

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_VOICE_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_VOICE_ENABLED, enabled).apply()
    }

    fun volume(context: Context): Float =
        prefs(context).getFloat(KEY_VOLUME, DEFAULT_VOLUME).coerceIn(MIN_VOLUME, MAX_VOLUME)

    fun setVolume(context: Context, value: Float) {
        prefs(context).edit().putFloat(KEY_VOLUME, value.coerceIn(MIN_VOLUME, MAX_VOLUME)).apply()
    }

    fun pitch(context: Context): Float =
        prefs(context).getFloat(KEY_PITCH, DEFAULT_PITCH).coerceIn(MIN_PITCH, MAX_PITCH)

    fun setPitch(context: Context, value: Float) {
        prefs(context).edit().putFloat(KEY_PITCH, value.coerceIn(MIN_PITCH, MAX_PITCH)).apply()
    }

    fun speechRate(context: Context): Float =
        prefs(context).getFloat(KEY_SPEECH_RATE, DEFAULT_SPEECH_RATE).coerceIn(MIN_SPEECH_RATE, MAX_SPEECH_RATE)

    fun setSpeechRate(context: Context, value: Float) {
        prefs(context).edit().putFloat(KEY_SPEECH_RATE, value.coerceIn(MIN_SPEECH_RATE, MAX_SPEECH_RATE)).apply()
    }

    /** Vuelve volumen, tono y velocidad a sus valores por defecto (no toca el interruptor de silencio). */
    fun resetToDefaults(context: Context) {
        prefs(context).edit()
            .putFloat(KEY_VOLUME, DEFAULT_VOLUME)
            .putFloat(KEY_PITCH, DEFAULT_PITCH)
            .putFloat(KEY_SPEECH_RATE, DEFAULT_SPEECH_RATE)
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
