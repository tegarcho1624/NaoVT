package com.naovt.app.voice

import android.content.Context
import android.media.AudioAttributes
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
 * - [isSpeaking] es observable desde Compose (sirve para animar la boca).
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
    private var pendingText: String? = null
    private var currentUtteranceId: String? = null
    private var utteranceCounter = 0

    private val appContext = context.applicationContext

    private val tts: TextToSpeech = TextToSpeech(appContext) { status ->
        if (status == TextToSpeech.SUCCESS) onEngineReady() else problem = Problem.EngineFailed
    }

    /**
     * Dice [text]. Si Nao ya está hablando, corta la frase anterior y empieza la nueva.
     */
    fun speak(text: String) {
        if (problem != null) return
        if (!VoicePreferences.isEnabled(appContext)) return // voz silenciada desde el menú
        if (!ready) {
            pendingText = text // se dirá apenas el motor esté listo
            return
        }
        val id = "nao-${++utteranceCounter}"
        currentUtteranceId = id
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    /** Libera el motor. Llamar cuando la pantalla se destruye. */
    fun shutdown() {
        tts.stop()
        tts.shutdown()
        isSpeaking = false
    }

    private fun onEngineReady() {
        if (!selectSpanish()) {
            problem = Problem.SpanishUnavailable
            return
        }
        tts.setPitch(VOICE_PITCH)
        tts.setSpeechRate(VOICE_SPEECH_RATE)
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        tts.setOnUtteranceProgressListener(progressListener)
        ready = true
        pendingText?.let { speak(it) }
        pendingText = null
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

    // Los callbacks llegan en un hilo secundario. Solo se hace caso a la frase actual,
    // porque al cortar una frase, la anterior avisa que terminó (onStop) después de empezar la nueva.
    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            if (utteranceId == currentUtteranceId) isSpeaking = true
        }

        override fun onDone(utteranceId: String?) {
            if (utteranceId == currentUtteranceId) isSpeaking = false
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            if (utteranceId == currentUtteranceId) isSpeaking = false
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            if (utteranceId == currentUtteranceId) isSpeaking = false
        }
    }

    companion object {
        private const val TAG = "NaoVoice"

        /** 1.0 = normal. Un poco más agudo para sonar más tierna. */
        const val VOICE_PITCH = 1.15f

        /** 1.0 = normal. Ligeramente más lento para sonar cálida y no atropellada. */
        const val VOICE_SPEECH_RATE = 0.95f
    }
}
