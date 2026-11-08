package com.naovt.app.pomodoro

import android.content.Context
import com.naovt.app.R
import com.naovt.app.alerts.AlertSpeaker

/**
 * Reglas del Modo Estudio. Las usan la pantalla y el receptor de la alarma, y todas trabajan sobre
 * el estado guardado, así que da igual quién las llame ni si la app estaba abierta.
 *
 * Ciclo: estudio -> descanso -> estudio... hasta que el usuario lo reinicie.
 */
object PomodoroEngine {

    // Una alarma exacta no llega antes de hora; este margen solo cubre diferencias mínimas de reloj.
    private const val EARLY_TOLERANCE_MS = 500L

    // Si el aviso llegó con más retraso que esto (teléfono apagado, por ejemplo), no se habla ni se
    // suena por fases viejas: solo se pone la cuenta al día.
    private const val LATE_LIMIT_MS = 5 * 60_000L

    /** Inicia o continúa. Si es un ciclo nuevo, Nao anima con la voz. */
    fun start(context: Context) {
        val store = PomodoroStore(context)
        val state = store.load()
        if (state.running) return

        val freshCycle = state.phase == PomodoroPhase.Study && state.remainingMillis >= state.phaseMillis()
        val started = state.copy(
            running = true,
            endsAtMillis = System.currentTimeMillis() + state.remainingMillis,
            cheerSerial = if (freshCycle) state.cheerSerial + 1 else state.cheerSerial,
        )
        store.save(started)
        PomodoroScheduler.schedule(context, started.endsAtMillis)
        PomodoroNotifier.showOngoing(context, started)
        if (freshCycle) speak(context, context.getString(R.string.pomodoro_say_start))
    }

    fun pause(context: Context) {
        val store = PomodoroStore(context)
        val state = store.load()
        if (!state.running) return

        val remaining = (state.endsAtMillis - System.currentTimeMillis()).coerceAtLeast(1000L)
        store.save(state.copy(running = false, remainingMillis = remaining))
        PomodoroScheduler.cancel(context)
        PomodoroNotifier.cancelOngoing(context)
    }

    /** Vuelve al inicio (estudio con el tiempo completo), conservando las duraciones elegidas. */
    fun reset(context: Context) {
        val store = PomodoroStore(context)
        val state = store.load()
        PomodoroScheduler.cancel(context)
        PomodoroNotifier.cancelOngoing(context)
        store.save(
            PomodoroState(
                studyMinutes = state.studyMinutes,
                breakMinutes = state.breakMinutes,
                remainingMillis = state.studyMinutes * 60_000L,
                cheerSerial = state.cheerSerial,
            )
        )
    }

    /** Cambia las duraciones. Solo se permite antes de empezar (estado "fresco"). */
    fun setDurations(context: Context, studyMinutes: Int, breakMinutes: Int) {
        val store = PomodoroStore(context)
        val state = store.load()
        if (!state.isFresh) return
        val study = studyMinutes.coerceIn(PomodoroState.MIN_MINUTES, PomodoroState.MAX_STUDY_MINUTES)
        val breakTime = breakMinutes.coerceIn(PomodoroState.MIN_MINUTES, PomodoroState.MAX_BREAK_MINUTES)
        store.save(state.copy(studyMinutes = study, breakMinutes = breakTime, remainingMillis = study * 60_000L))
    }

    /**
     * Si la fase actual ya terminó, pasa a la siguiente. Lo llama el receptor de la alarma y, por
     * si la alarma se retrasó, la pantalla. Es seguro llamarlo varias veces: solo avanza una vez.
     * [onDone] se llama cuando ya no queda nada por hacer (incluida la voz).
     */
    fun advanceIfDue(context: Context, onDone: () -> Unit = {}) {
        val store = PomodoroStore(context)
        val state = store.load()
        val now = System.currentTimeMillis()
        if (!state.running || now + EARLY_TOLERANCE_MS < state.endsAtMillis) {
            onDone()
            return
        }
        if (now - state.endsAtMillis > LATE_LIMIT_MS) {
            catchUp(context, store, state, now)
            onDone()
        } else {
            phaseEnded(context, store, state, now, onDone)
        }
    }

    /** Tras reiniciar el teléfono las alarmas se borran: se reprograma la fase en curso. */
    fun restore(context: Context) {
        val state = PomodoroStore(context).load()
        if (!state.running) return
        if (System.currentTimeMillis() >= state.endsAtMillis) {
            advanceIfDue(context)
        } else {
            PomodoroScheduler.schedule(context, state.endsAtMillis)
            PomodoroNotifier.showOngoing(context, state)
        }
    }

    private fun phaseEnded(
        context: Context,
        store: PomodoroStore,
        state: PomodoroState,
        now: Long,
        onDone: () -> Unit,
    ) {
        val ended = state.phase
        val next = if (ended == PomodoroPhase.Study) PomodoroPhase.Break else PomodoroPhase.Study
        val nextState = state.copy(
            phase = next,
            endsAtMillis = now + state.phaseMillis(next),
            remainingMillis = state.phaseMillis(next),
            completedPomodoros = state.completedPomodoros + if (ended == PomodoroPhase.Study) 1 else 0,
            cheerSerial = state.cheerSerial + 1,
        )
        // Lo primero: dejar programada la siguiente fase, así el ciclo no se rompe aunque algo falle.
        store.save(nextState)
        PomodoroScheduler.schedule(context, nextState.endsAtMillis)
        PomodoroNotifier.showOngoing(context, nextState)
        PomodoroNotifier.showPhaseEnd(context, ended, nextState)

        val text = if (ended == PomodoroPhase.Study) {
            context.getString(
                R.string.pomodoro_say_break,
                context.resources.getQuantityString(R.plurals.pomodoro_minutes, nextState.breakMinutes, nextState.breakMinutes),
            )
        } else {
            context.getString(R.string.pomodoro_say_focus)
        }
        speak(context, text, onDone)
    }

    /** Pone la cuenta al día sin sonar ni hablar por fases que ya pasaron hace mucho. */
    private fun catchUp(context: Context, store: PomodoroStore, state: PomodoroState, now: Long) {
        var phase = state.phase
        var endsAt = state.endsAtMillis
        var completed = state.completedPomodoros
        var steps = 0
        while (now >= endsAt && steps < MAX_CATCH_UP_STEPS) {
            if (phase == PomodoroPhase.Study) completed++
            phase = if (phase == PomodoroPhase.Study) PomodoroPhase.Break else PomodoroPhase.Study
            endsAt += state.phaseMillis(phase)
            steps++
        }
        val updated = state.copy(
            phase = phase,
            endsAtMillis = endsAt,
            remainingMillis = endsAt - now,
            completedPomodoros = completed,
            cheerSerial = state.cheerSerial + 1,
        )
        store.save(updated)
        PomodoroScheduler.schedule(context, endsAt)
        PomodoroNotifier.showOngoing(context, updated)
    }

    private fun speak(context: Context, text: String, onDone: () -> Unit = {}) {
        // Mismo motor y misma voz de Nao que las alertas; respeta el interruptor de silencio.
        // No abre ninguna pantalla, así que no interrumpe lo que el usuario esté haciendo.
        AlertSpeaker(context).speak(text, onDone)
    }

    private const val MAX_CATCH_UP_STEPS = 10_000
}
