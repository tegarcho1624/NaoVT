package com.naovt.app.alerts

import com.naovt.app.data.AlertRepeat
import java.util.Calendar

/** Calcula cuándo toca la siguiente vez de una alerta que se repite. */
object AlertRecurrence {

    // Tope de seguridad por si el teléfono estuvo apagado mucho tiempo.
    private const val MAX_STEPS = 20_000

    /** Primer disparo: en "lunes a viernes", si el día elegido cae en fin de semana pasa al lunes. */
    fun alignFirst(triggerAt: Long, repeat: AlertRepeat): Long =
        if (repeat == AlertRepeat.Weekdays) skipWeekend(calendarOf(triggerAt)).timeInMillis else triggerAt

    /**
     * Siguiente disparo después de [after], conservando la hora del día (también con cambios de horario).
     * Si [repeat] es None devuelve [triggerAt] sin cambios.
     */
    fun nextAfter(triggerAt: Long, repeat: AlertRepeat, after: Long): Long {
        if (repeat == AlertRepeat.None) return triggerAt

        val calendar = calendarOf(triggerAt)
        var steps = 0
        do {
            when (repeat) {
                AlertRepeat.Weekly -> calendar.add(Calendar.DAY_OF_YEAR, 7)
                AlertRepeat.Daily, AlertRepeat.Weekdays -> calendar.add(Calendar.DAY_OF_YEAR, 1)
                AlertRepeat.None -> Unit
            }
            if (repeat == AlertRepeat.Weekdays) skipWeekend(calendar)
            steps++
        } while (calendar.timeInMillis <= after && steps < MAX_STEPS)
        return calendar.timeInMillis
    }

    private fun calendarOf(millis: Long): Calendar =
        Calendar.getInstance().apply { timeInMillis = millis }

    private fun skipWeekend(calendar: Calendar): Calendar {
        while (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
            calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
        ) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return calendar
    }
}
