package com.naovt.app.ui.pomodoro

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.naovt.app.pomodoro.PomodoroEngine
import com.naovt.app.pomodoro.PomodoroState
import com.naovt.app.pomodoro.PomodoroStore

/** Puente entre la pantalla y el [PomodoroEngine]: cada acción termina refrescando el estado. */
class PomodoroViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val store = PomodoroStore(application)

    var state by mutableStateOf(store.load())
        private set

    /** Relee el estado; si una fase ya terminó y la alarma se retrasó, la avanza. */
    fun refresh() {
        PomodoroEngine.advanceIfDue(app)
        state = store.load()
    }

    fun start() = act { PomodoroEngine.start(app) }
    fun pause() = act { PomodoroEngine.pause(app) }
    fun reset() = act { PomodoroEngine.reset(app) }

    fun setDurations(studyMinutes: Int, breakMinutes: Int) =
        act { PomodoroEngine.setDurations(app, studyMinutes, breakMinutes) }

    private fun act(action: () -> Unit) {
        action()
        state = store.load()
    }

    companion object {
        const val STEP_MINUTES = 5
    }
}
