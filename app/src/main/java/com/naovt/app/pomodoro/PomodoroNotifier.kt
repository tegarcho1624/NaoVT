package com.naovt.app.pomodoro

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.naovt.app.MainActivity
import com.naovt.app.R
import com.naovt.app.alerts.AlertFaceBitmap
import com.naovt.app.alerts.AlertNotifier
import com.naovt.app.ui.avatar.NaoExpression

/**
 * Notificaciones del Modo Estudio:
 * - La persistente muestra la cuenta regresiva con el cronómetro del propio sistema, así que no hay
 *   que actualizarla cada segundo (no gasta batería).
 * - La de fin de fase suena (canal de importancia alta) cuando termina estudio o descanso.
 */
object PomodoroNotifier {

    private const val CHANNEL_ONGOING = "nao_pomodoro"
    private const val CHANNEL_PHASE_END = "nao_pomodoro_phase_end"
    private const val ONGOING_ID = 30001
    private const val PHASE_END_ID = 30002

    // Códigos de PendingIntent altos para no chocar con los de las alertas (que usan el id de cada alerta).
    private const val REQUEST_OPEN = 900_001
    private const val REQUEST_PAUSE = 900_002
    private const val REQUEST_STOP = 900_003

    /** Notificación persistente con el tiempo que falta de la fase actual. */
    fun showOngoing(context: Context, state: PomodoroState) {
        if (!AlertNotifier.canNotify(context)) return
        ensureChannels(context)

        val title = context.getString(
            if (state.phase == PomodoroPhase.Study) R.string.pomodoro_status_study
            else R.string.pomodoro_status_break
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_nao)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.pomodoro_notification_text))
            .setContentIntent(openIntent(context))
            .addAction(R.drawable.ic_stat_nao, context.getString(R.string.pomodoro_pause), receiverIntent(context, PomodoroReceiver.ACTION_PAUSE, REQUEST_PAUSE))
            .addAction(R.drawable.ic_stat_nao, context.getString(R.string.pomodoro_notification_stop), receiverIntent(context, PomodoroReceiver.ACTION_STOP, REQUEST_STOP))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            // Cuenta regresiva que dibuja el sistema hasta la hora de fin.
            .setShowWhen(true)
            .setWhen(state.endsAtMillis)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .build()
        notify(context, ONGOING_ID, notification)
    }

    fun cancelOngoing(context: Context) {
        NotificationManagerCompat.from(context).cancel(ONGOING_ID)
    }

    /** Aviso con sonido al terminar una fase. [ended] es la que acaba de terminar. */
    fun showPhaseEnd(context: Context, ended: PomodoroPhase, newState: PomodoroState) {
        if (!AlertNotifier.canNotify(context)) return
        ensureChannels(context)

        val studyEnded = ended == PomodoroPhase.Study
        val title = context.getString(
            if (studyEnded) R.string.pomodoro_end_study_title else R.string.pomodoro_end_break_title
        )
        val text = if (studyEnded) {
            context.getString(
                R.string.pomodoro_end_study_text,
                context.resources.getQuantityString(R.plurals.pomodoro_minutes, newState.breakMinutes, newState.breakMinutes),
            )
        } else {
            context.getString(R.string.pomodoro_end_break_text)
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_PHASE_END)
            .setSmallIcon(R.drawable.ic_stat_nao)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openIntent(context))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        AlertFaceBitmap.build(context, NaoExpression.Neutral)?.let { builder.setLargeIcon(it) }
        notify(context, PHASE_END_ID, builder.build())
    }

    private fun notify(context: Context, id: Int, notification: android.app.Notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // El permiso se retiró justo ahora: la voz igual avisa.
        }
    }

    private fun openIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_OPEN,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_DESTINATION, MainActivity.DESTINATION_POMODORO),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun receiverIntent(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, PomodoroReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ONGOING,
                context.getString(R.string.pomodoro_channel_ongoing),
                NotificationManager.IMPORTANCE_LOW, // silenciosa: solo muestra el tiempo
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PHASE_END,
                context.getString(R.string.pomodoro_channel_phase_end),
                NotificationManager.IMPORTANCE_HIGH, // con sonido y aviso en pantalla
            )
        )
    }
}
