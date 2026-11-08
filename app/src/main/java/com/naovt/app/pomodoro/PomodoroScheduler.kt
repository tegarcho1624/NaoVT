package com.naovt.app.pomodoro

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Programa con AlarmManager el fin de la fase actual. Es lo que permite que el cambio de fase y la
 * voz de Nao ocurran con la app cerrada, sin un servicio en primer plano corriendo todo el tiempo.
 */
object PomodoroScheduler {

    private const val TAG = "PomodoroScheduler"
    private const val REQUEST_PHASE_END = 1

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager(context).canScheduleExactAlarms()

    fun schedule(context: Context, triggerAtMillis: Long) {
        val manager = alarmManager(context)
        val pendingIntent = phaseEndIntent(context, create = true) ?: return
        if (canScheduleExact(context)) {
            try {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                return
            } catch (e: SecurityException) {
                Log.w(TAG, "Permiso de alarmas exactas retirado; se usa alarma aproximada", e)
            }
        }
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }

    fun cancel(context: Context) {
        phaseEndIntent(context, create = false)?.let {
            alarmManager(context).cancel(it)
            it.cancel()
        }
    }

    private fun alarmManager(context: Context): AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun phaseEndIntent(context: Context, create: Boolean): PendingIntent? {
        val intent = Intent(context, PomodoroReceiver::class.java)
            .setAction(PomodoroReceiver.ACTION_PHASE_END)
        val flags = PendingIntent.FLAG_IMMUTABLE or
            if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, REQUEST_PHASE_END, intent, flags)
    }
}
