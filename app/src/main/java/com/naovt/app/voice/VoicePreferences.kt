package com.naovt.app.voice

import android.content.Context

/** Interruptor de "voz activada / silenciada" para Nao. Se guarda y se respeta en toda la app. */
object VoicePreferences {

    private const val PREFS_NAME = "naovt_settings"
    private const val KEY_VOICE_ENABLED = "voice_enabled"

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_VOICE_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_VOICE_ENABLED, enabled).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
