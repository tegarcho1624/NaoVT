package com.naovt.app.ui.rooms

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.naovt.app.R
import com.naovt.app.data.Habitacion
import com.naovt.app.data.RoomRepository
import com.naovt.app.data.TipoHabitacion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Archivo ya copiado a la carpeta interna que está esperando su nombre. */
data class PendingRoom(val path: String, val tipo: TipoHabitacion)

/**
 * Estado de las habitaciones, compartido por la pantalla principal (fondo activo) y la pantalla
 * "Gestionar Habitaciones" (lista, añadir y eliminar).
 */
class RoomsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val repository = RoomRepository(application)

    var habitaciones by mutableStateOf(repository.getAll())
        private set

    var activeId by mutableStateOf(repository.activeId())
        private set

    /** Copiando un archivo (puede tardar con videos grandes). */
    var copying by mutableStateOf(false)
        private set

    var pending by mutableStateOf<PendingRoom?>(null)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    /** La habitación activa. Si no se encuentra (o perdió su archivo) se usa la Sala de estar. */
    val activa: Habitacion
        get() = habitaciones.firstOrNull { it.id == activeId }
            ?: habitaciones.first { it.id == RoomRepository.ID_SALA }

    val oficiales: List<Habitacion> get() = habitaciones.filter { it.esPredeterminada }
    val propias: List<Habitacion> get() = habitaciones.filterNot { it.esPredeterminada }

    fun select(id: String) {
        repository.setActive(id)
        activeId = id
    }

    /** Copia el archivo elegido a la carpeta interna; después la pantalla pide el nombre. */
    fun onFilePicked(uri: Uri, tipo: TipoHabitacion) {
        copying = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repository.copyToInternal(uri, tipo) }
            copying = false
            result
                .onSuccess { file -> pending = PendingRoom(file.absolutePath, tipo) }
                .onFailure { e ->
                    Log.w(TAG, "No se pudo copiar el archivo", e)
                    error = app.getString(R.string.rooms_copy_failed)
                }
        }
    }

    fun saveRoom(name: String) {
        val room = pending ?: return
        repository.add(name.trim(), room.tipo, room.path)
        pending = null
        refresh()
    }

    /** Si se cancela el nombre, se borra la copia para no dejar archivos sueltos. */
    fun cancelPending() {
        pending?.let { File(it.path).delete() }
        pending = null
    }

    fun delete(habitacion: Habitacion) {
        if (repository.delete(habitacion.id)) refresh() // si era la activa, vuelve a la Sala de estar
    }

    fun clearError() {
        error = null
    }

    private fun refresh() {
        habitaciones = repository.getAll()
        activeId = repository.activeId()
    }

    private companion object {
        const val TAG = "RoomsViewModel"
    }
}
