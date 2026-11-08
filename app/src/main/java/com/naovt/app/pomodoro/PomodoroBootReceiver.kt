package com.naovt.app.pomodoro

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Al reiniciar el teléfono (o actualizar la app) vuelve a programar el Modo Estudio si estaba en marcha. */
class PomodoroBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> PomodoroEngine.restore(context)
        }
    }
}
