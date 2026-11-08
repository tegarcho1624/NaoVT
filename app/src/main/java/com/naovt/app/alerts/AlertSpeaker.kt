package com.naovt.app.alerts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.naovt.app.voice.NaoVoice
import com.naovt.app.voice.VoicePreferences

/**
 * Dice un texto con el mismo motor de voz de Nao y lo libera al terminar.
 * Pensado para el receptor de la alarma: avisa con [speak]'s `onFinished` cuando ya se puede
 * cerrar el receptor. Si la voz falla, está silenciada o tarda demasiado, termina igual.
 */
class AlertSpeaker(context: Context) {

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())

    private var voice: NaoVoice? = null
    private var onFinished: (() -> Unit)? = null
    private var started = false
    private var startedAt = 0L

    fun speak(text: String, onFinished: () -> Unit) {
        // Voz silenciada desde el menú, o nada que decir: no se crea el motor.
        if (text.isBlank() || !VoicePreferences.isEnabled(appContext)) {
            onFinished()
            return
        }
        this.onFinished = onFinished
        voice = NaoVoice(appContext).also { it.speak(text) } // si aún no está listo, la frase espera
        startedAt = SystemClock.uptimeMillis()
        handler.post(poll)
    }

    // Revisa cada cierto tiempo si ya terminó (o si hay que rendirse).
    private val poll = object : Runnable {
        override fun run() {
            val engine = voice ?: return
            if (engine.isSpeaking) started = true
            val waited = SystemClock.uptimeMillis() - startedAt
            when {
                engine.problem != null -> finish()
                started && !engine.isSpeaking -> finish()
                waited > MAX_WAIT_MS -> finish()
                else -> handler.postDelayed(this, POLL_MS)
            }
        }
    }

    private fun finish() {
        handler.removeCallbacks(poll)
        voice?.shutdown() // libera el motor
        voice = null
        val callback = onFinished
        onFinished = null
        callback?.invoke()
    }

    private companion object {
        const val POLL_MS = 250L

        // Un receptor puede vivir ~10 s con goAsync(): se corta antes para cerrar a tiempo.
        const val MAX_WAIT_MS = 8500L
    }
}
