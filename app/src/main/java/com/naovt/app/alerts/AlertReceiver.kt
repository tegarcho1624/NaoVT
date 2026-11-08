package com.naovt.app.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.naovt.app.data.AlertRepeat
import com.naovt.app.data.AlertRepository
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Se dispara cuando llega la hora de una alerta: muestra la notificación y dice el texto con la voz
 * de Nao, aunque la app esté en segundo plano. Usa goAsync() para que el sistema no cierre el
 * receptor mientras habla.
 */
class AlertReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alertId = intent.getIntExtra(AlertScheduler.EXTRA_ALERT_ID, -1)
        if (alertId == -1) return

        val repository = AlertRepository(context)
        val alert = repository.get(alertId) ?: return // la borraron antes de que sonara
        if (alert.fired) return

        if (alert.repeat == AlertRepeat.None) {
            repository.markFired(alertId)
        } else {
            // Lo primero: dejar programada la próxima vez, así la cadena no se rompe aunque algo falle.
            val next = AlertRecurrence.nextAfter(alert.triggerAtMillis, alert.repeat, System.currentTimeMillis())
            repository.updateTrigger(alertId, next)
            AlertScheduler.schedule(context, alert.copy(triggerAtMillis = next))
        }
        AlertNotifier.show(context, alert)

        val pending = goAsync()
        val done = AtomicBoolean(false)
        val finish = {
            if (done.compareAndSet(false, true)) pending.finish()
        }
        try {
            AlertSpeaker(context).speak(alert.text) { finish() }
        } catch (e: Exception) {
            finish()
        }
    }
}
