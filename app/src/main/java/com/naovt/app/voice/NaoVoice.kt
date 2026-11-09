package com.naovt.app.voice

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Voz de Nao basada en el TextToSpeech nativo de Android.
 *
 * - [speak] corta lo que estuviera diciendo y empieza una frase nueva.
 * - [enqueue] pone una frase en cola, después de las que ya se están diciendo (para leer
 *   una respuesta larga oración por oración, mientras todavía se está escribiendo).
 * - [isSpeaking] es observable desde Compose (sirve para animar la boca) y es true mientras
 *   quede alguna frase por decir.
 * - [problem] indica si la voz no se pudo preparar (por ejemplo, sin voz en español).
 * - No requiere permisos especiales.
 */
class NaoVoice(context: Context) {

    enum class Problem { EngineFailed, SpanishUnavailable }

    var isSpeaking by mutableStateOf(false)
        private set

    var problem by mutableStateOf<Problem?>(null)
        private set

    private var ready = false
    private val pending = ArrayList<Pair<String, Int>>() // frases pedidas antes de que el motor esté listo
    private val activeIds = HashSet<String>() // frases en curso; se toca desde varios hilos
    private val lock = Any()
    private var utteranceCounter = 0

    private val appContext = context.applicationContext

    private val tts: TextToSpeech = TextToSpeech(appContext) { status ->
        if (status == TextToSpeech.SUCCESS) onEngineReady() else problem = Problem.EngineFailed
    }

    /** Dice [text]. Si Nao ya está hablando, corta lo anterior y empieza la nueva. */
    fun speak(text: String) = submit(text, TextToSpeech.QUEUE_FLUSH)

    /** Dice [text] después de lo que ya se esté diciendo. */
    fun enqueue(text: String) = submit(text, TextToSpeech.QUEUE_ADD)

    /** Calla a Nao de inmediato y borra lo que estuviera en cola. */
    fun stop() {
        pending.clear()
        synchronized(lock) {
            activeIds.clear()
            isSpeaking = false
        }
        if (ready) tts.stop()
    }

    /** Libera el motor. Llamar cuando la pantalla se destruye. */
    fun shutdown() {
        pending.clear()
        tts.stop()
        tts.shutdown()
        synchronized(lock) {
            activeIds.clear()
            isSpeaking = false
        }
    }

    private fun submit(text: String, mode: Int) {
        if (problem != null) return
        if (!VoicePreferences.isEnabled(appContext)) return // voz silenciada desde el menú
        if (!ready) {
            // Se dirá apenas el motor esté listo.
            if (mode == TextToSpeech.QUEUE_FLUSH) pending.clear()
            pending.add(text to mode)
            return
        }
        val id = "nao-${++utteranceCounter}"
        synchronized(lock) {
            if (mode == TextToSpeech.QUEUE_FLUSH) activeIds.clear()
            activeIds.add(id)
        }
        // Los ajustes de voz se leen cada vez que Nao habla: un cambio en "Ajustes de Voz"
        // suena en la siguiente frase, en las frases, las alertas y el Modo Estudio por igual.
        tts.setPitch(VoicePreferences.pitch(appContext))
        tts.setSpeechRate(VoicePreferences.speechRate(appContext))
        val params = Bundle().apply {
            // Volumen relativo (0.0 a 1.0) al volumen multimedia del teléfono.
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, VoicePreferences.volume(appContext))
        }
        tts.speak(text, mode, params, id)
    }

    private fun onEngineReady() {
        if (!selectSpanish()) {
            problem = Problem.SpanishUnavailable
            return
        }
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        tts.setOnUtteranceProgressListener(progressListener)
        ready = true
        val queued = ArrayList(pending)
        pending.clear()
        queued.forEach { (text, mode) -> submit(text, mode) }
    }

    /** Intenta es-MX, luego es-ES y por último cualquier español disponible. */
    private fun selectSpanish(): Boolean {
        val candidates = listOf(Locale("es", "MX"), Locale("es", "ES"), Locale("es"))
        for (locale in candidates) {
            val result = tts.setLanguage(locale)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.d(TAG, "Voz en ${locale.toLanguageTag()}")
                return true
            }
        }
        return false
    }

    // Los avisos llegan en un hilo secundario. Cada frase se apunta por su id: cuando ya no queda
    // ninguna en curso, Nao deja de "hablar". Los avisos de frases cortadas (que ya no están en la
    // lista) no cambian nada.
    private fun finish(utteranceId: String?) {
        synchronized(lock) {
            activeIds.remove(utteranceId)
            if (activeIds.isEmpty()) isSpeaking = false
        }
    }

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            synchronized(lock) {
                if (utteranceId in activeIds) isSpeaking = true
            }
        }

        override fun onDone(utteranceId: String?) = finish(utteranceId)

        override fun onStop(utteranceId: String?, interrupted: Boolean) = finish(utteranceId)

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = finish(utteranceId)
    }

    companion object {
        private const val TAG = "NaoVoice"
    }
}
