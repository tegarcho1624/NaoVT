package com.naovt.app.pomodoro

import android.content.Context

/** Guarda y lee el [PomodoroState] en SharedPreferences. */
class PomodoroStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): PomodoroState = synchronized(LOCK) {
        val study = prefs.getInt(KEY_STUDY, PomodoroState.DEFAULT_STUDY_MINUTES)
        val breakMinutes = prefs.getInt(KEY_BREAK, PomodoroState.DEFAULT_BREAK_MINUTES)
        PomodoroState(
            phase = runCatching {
                PomodoroPhase.valueOf(prefs.getString(KEY_PHASE, "") ?: "")
            }.getOrDefault(PomodoroPhase.Study),
            running = prefs.getBoolean(KEY_RUNNING, false),
            endsAtMillis = prefs.getLong(KEY_ENDS_AT, 0L),
            remainingMillis = prefs.getLong(KEY_REMAINING, study * 60_000L),
            studyMinutes = study,
            breakMinutes = breakMinutes,
            completedPomodoros = prefs.getInt(KEY_COMPLETED, 0),
            cheerSerial = prefs.getInt(KEY_CHEER, 0),
        )
    }

    fun save(state: PomodoroState) = synchronized(LOCK) {
        prefs.edit()
            .putString(KEY_PHASE, state.phase.name)
            .putBoolean(KEY_RUNNING, state.running)
            .putLong(KEY_ENDS_AT, state.endsAtMillis)
            .putLong(KEY_REMAINING, state.remainingMillis)
            .putInt(KEY_STUDY, state.studyMinutes)
            .putInt(KEY_BREAK, state.breakMinutes)
            .putInt(KEY_COMPLETED, state.completedPomodoros)
            .putInt(KEY_CHEER, state.cheerSerial)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "naovt_pomodoro"
        const val KEY_PHASE = "phase"
        const val KEY_RUNNING = "running"
        const val KEY_ENDS_AT = "ends_at"
        const val KEY_REMAINING = "remaining"
        const val KEY_STUDY = "study_minutes"
        const val KEY_BREAK = "break_minutes"
        const val KEY_COMPLETED = "completed"
        const val KEY_CHEER = "cheer_serial"
        val LOCK = Any()
    }
}
