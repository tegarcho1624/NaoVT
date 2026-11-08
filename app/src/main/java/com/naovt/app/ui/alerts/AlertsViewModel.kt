package com.naovt.app.ui.alerts

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.naovt.app.alerts.AlertScheduler
import com.naovt.app.data.Alert
import com.naovt.app.data.AlertRepository
import com.naovt.app.ui.avatar.NaoExpression
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Avisos de un solo uso para la pantalla. */
enum class AlertsEvent {
    /** Se guardó, pero sin permiso de alarmas exactas puede sonar con unos minutos de retraso. */
    NoExactPermission,
}

/** Lógica de Mis Alertas: lista, guardar (con su alarma) y eliminar (cancelando la alarma). */
class AlertsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val repository = AlertRepository(application)

    var alerts by mutableStateOf<List<Alert>>(emptyList())
        private set

    private val eventChannel = Channel<AlertsEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    init {
        loadAlerts()
    }

    fun addAlert(text: String, triggerAtMillis: Long, expression: NaoExpression) {
        viewModelScope.launch {
            val alert = withContext(Dispatchers.IO) {
                repository.add(text.trim(), triggerAtMillis, expression)
            }
            val exact = AlertScheduler.schedule(app, alert)
            alerts = (alerts + alert).sortedBy { it.triggerAtMillis }
            if (!exact) eventChannel.send(AlertsEvent.NoExactPermission)
        }
    }

    fun deleteAlert(alert: Alert) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.delete(alert.id) }
            AlertScheduler.cancel(app, alert.id)
            alerts = alerts.filterNot { it.id == alert.id }
        }
    }

    /**
     * Al volver a la pantalla: recarga la lista (por si alguna ya sonó) y vuelve a programar las
     * pendientes (por si el usuario acaba de conceder el permiso de alarmas exactas).
     */
    fun onScreenResumed() {
        viewModelScope.launch {
            alerts = withContext(Dispatchers.IO) {
                AlertScheduler.rescheduleAll(app)
                repository.getAll()
            }
        }
    }

    private fun loadAlerts() {
        viewModelScope.launch {
            alerts = withContext(Dispatchers.IO) { repository.getAll() }
        }
    }
}
