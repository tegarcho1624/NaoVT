package com.naovt.app.alerts

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.naovt.app.data.Alert
import com.naovt.app.data.AlertRepository

/** Programa y cancela las alarmas de las alertas con AlarmManager. */
object AlertScheduler {

    private const val TAG = "AlertScheduler"
    private const val ACTION_ALERT = "com.naovt.app.action.ALERT"
    const val EXTRA_ALERT_ID = "com.naovt.app.extra.ALERT_ID"

    /** Android 12+ pide un permiso especial para alarmas exactas; antes siempre está permitido. */
    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager(context).canScheduleExactAlarms()

    /**
     * Programa la alarma de [alert].
     * @return true si quedó exacta; false si no hay permiso y se programó una alarma aproximada
     *         (puede retrasarse unos minutos).
     */
    fun schedule(context: Context, alert: Alert): Boolean {
        val manager = alarmManager(context)
        val pendingIntent = pendingIntent(context, alert.id, create = true) ?: return false
        if (canScheduleExact(context)) {
            try {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alert.triggerAtMillis, pendingIntent)
                return true
            } catch (e: SecurityException) {
                Log.w(TAG, "Permiso de alarmas exactas retirado; se usa alarma aproximada", e)
            }
        }
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alert.triggerAtMillis, pendingIntent)
        return false
    }

    fun cancel(context: Context, alertId: Int) {
        pendingIntent(context, alertId, create = false)?.let {
            alarmManager(context).cancel(it)
            it.cancel()
        }
    }

    /**
     * Vuelve a programar todas las alertas pendientes (tras reiniciar el teléfono, actualizar la app
     * o conceder el permiso). Con [handleMissed] las que ya pasaron se avisan como "se pasó".
     */
    fun rescheduleAll(context: Context, handleMissed: Boolean = false) {
        val repository = AlertRepository(context)
        val now = System.currentTimeMillis()
        repository.getAll().filter { !it.fired }.forEach { alert ->
            if (alert.triggerAtMillis > now) {
                schedule(context, alert)
            } else if (handleMissed) {
                repository.markFired(alert.id)
                AlertNotifier.show(context, alert, missed = true)
            }
        }
    }

    private fun alarmManager(context: Context): AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun pendingIntent(context: Context, alertId: Int, create: Boolean): PendingIntent? {
        val intent = Intent(context, AlertReceiver::class.java)
            .setAction(ACTION_ALERT)
            .putExtra(EXTRA_ALERT_ID, alertId)
        val flags = PendingIntent.FLAG_IMMUTABLE or
            if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, alertId, intent, flags)
    }
}
