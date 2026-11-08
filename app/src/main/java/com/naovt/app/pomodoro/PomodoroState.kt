package com.naovt.app.pomodoro

enum class PomodoroPhase { Study, Break }

/**
 * Estado completo del Modo Estudio. Se guarda en SharedPreferences para que sobreviva si el sistema
 * cierra la app: lo que manda es la hora de fin ([endsAtMillis]), no una cuenta en memoria.
 */
data class PomodoroState(
    val phase: PomodoroPhase = PomodoroPhase.Study,
    val running: Boolean = false,
    /** Hora (reloj del teléfono) en que termina la fase actual. Solo vale si [running]. */
    val endsAtMillis: Long = 0L,
    /** Tiempo que falta de la fase actual cuando está en pausa o detenido. */
    val remainingMillis: Long = DEFAULT_STUDY_MINUTES * 60_000L,
    val studyMinutes: Int = DEFAULT_STUDY_MINUTES,
    val breakMinutes: Int = DEFAULT_BREAK_MINUTES,
    val completedPomodoros: Int = 0,
    /** Sube en cada inicio o cambio de fase: la pantalla lo usa para animar a Nao. */
    val cheerSerial: Int = 0,
) {
    fun phaseMillis(of: PomodoroPhase = phase): Long =
        (if (of == PomodoroPhase.Study) studyMinutes else breakMinutes) * 60_000L

    /** Sin empezar: detenido, en estudio y con el tiempo completo. */
    val isFresh: Boolean
        get() = !running && phase == PomodoroPhase.Study && remainingMillis >= phaseMillis()

    companion object {
        const val DEFAULT_STUDY_MINUTES = 25
        const val DEFAULT_BREAK_MINUTES = 5
        const val MIN_MINUTES = 1
        const val MAX_STUDY_MINUTES = 120
        const val MAX_BREAK_MINUTES = 60
    }
}
