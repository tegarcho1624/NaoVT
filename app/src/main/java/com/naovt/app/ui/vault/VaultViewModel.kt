package com.naovt.app.ui.vault

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.naovt.app.data.Memory
import com.naovt.app.data.MemoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Avisos de un solo uso para la pantalla del Baúl. */
enum class VaultEvent { Saved, SaveFailed }

/** Lógica del Baúl: carga, guarda y borra recuerdos sin bloquear la interfaz. */
class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MemoryRepository(application)

    var memories by mutableStateOf<List<Memory>>(emptyList())
        private set

    private val eventChannel = Channel<VaultEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            memories = withContext(Dispatchers.IO) { repository.getAll() }
        }
    }

    /** Copia la foto elegida a la carpeta interna y la agrega al Baúl. */
    fun addMemory(source: Uri, note: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repository.add(source, note.trim()) }
            result
                .onSuccess { memory ->
                    memories = listOf(memory) + memories
                    eventChannel.send(VaultEvent.Saved)
                }
                .onFailure { error ->
                    Log.e(TAG, "No se pudo guardar el recuerdo", error)
                    eventChannel.send(VaultEvent.SaveFailed)
                }
        }
    }

    fun deleteMemory(memory: Memory) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.delete(memory) }
            memories = memories.filterNot { it.id == memory.id }
        }
    }

    private companion object {
        const val TAG = "VaultViewModel"
    }
}
