package com.naovt.app.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Al reiniciar el teléfono (o actualizar la app) Android borra las alarmas: aquí se vuelven
 * a programar las alertas pendientes. Las que se pasaron mientras estaba apagado se avisan.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> AlertScheduler.rescheduleAll(context, handleMissed = true)
        }
    }
}
