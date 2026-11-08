package com.naovt.app.pomodoro

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Recibe el fin de una fase (alarma) y los botones "Pausar" y "Detener" de la notificación.
 * Para el fin de fase usa goAsync() para que el sistema no cierre el receptor mientras Nao habla.
 */
class PomodoroReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PHASE_END -> {
                val pending = goAsync()
                val done = AtomicBoolean(false)
                val finish = { if (done.compareAndSet(false, true)) pending.finish() }
                try {
                    PomodoroEngine.advanceIfDue(context) { finish() }
                } catch (e: Exception) {
                    finish()
                }
            }
            ACTION_PAUSE -> PomodoroEngine.pause(context)
            ACTION_STOP -> PomodoroEngine.reset(context)
        }
    }

    companion object {
        const val ACTION_PHASE_END = "com.naovt.app.action.POMODORO_PHASE_END"
        const val ACTION_PAUSE = "com.naovt.app.action.POMODORO_PAUSE"
        const val ACTION_STOP = "com.naovt.app.action.POMODORO_STOP"
    }
}
