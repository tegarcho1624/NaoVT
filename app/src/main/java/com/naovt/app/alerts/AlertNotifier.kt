package com.naovt.app.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.naovt.app.MainActivity
import com.naovt.app.R
import com.naovt.app.data.Alert

/** Notificación de una alerta: texto de la tarea y la carita de Nao con su expresión. */
object AlertNotifier {

    private const val CHANNEL_ID = "nao_alerts"
    private const val NOTIFICATION_BASE_ID = 20000

    /** En Android 13+ hace falta el permiso de notificaciones; antes siempre se puede. */
    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Con [missed] el título avisa que la alerta se pasó (por ejemplo, el teléfono estaba apagado). */
    fun show(context: Context, alert: Alert, missed: Boolean = false) {
        if (!canNotify(context)) return
        ensureChannel(context)

        // Al tocarla abre la app con Nao en la expresión elegida.
        val openApp = PendingIntent.getActivity(
            context,
            alert.id,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_ALERT_TEXT, alert.text)
                .putExtra(MainActivity.EXTRA_ALERT_EXPRESSION, alert.expression.name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val title = context.getString(
            if (missed) R.string.alert_missed_title else R.string.alert_notification_title
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_nao)
            .setContentTitle(title)
            .setContentText(alert.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.text))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        AlertFaceBitmap.build(context, alert.expression)?.let { builder.setLargeIcon(it) }

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_BASE_ID + alert.id, builder.build())
        } catch (e: SecurityException) {
            // El permiso se retiró justo ahora: no pasa nada, la voz igual avisa.
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.alert_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
