package com.naovt.app.ui.phrases

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.naovt.app.data.Phrase
import com.naovt.app.data.PhraseCategory
import com.naovt.app.data.PhraseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lógica del gestor de frases: carga, añade, edita y elimina sin bloquear la interfaz. */
class PhrasesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PhraseRepository(application)

    var phrases by mutableStateOf<List<Phrase>>(emptyList())
        private set

    init {
        reload()
    }

    fun addPhrase(text: String, category: PhraseCategory) = change {
        repository.add(text.trim(), category)
    }

    fun updatePhrase(id: Int, text: String, category: PhraseCategory) = change {
        repository.update(id, text.trim(), category)
    }

    fun deletePhrase(id: Int) = change { repository.delete(id) }

    private fun reload() = change { }

    /** Hace el cambio en segundo plano y deja la lista al día. */
    private fun change(action: () -> Unit) {
        viewModelScope.launch {
            phrases = withContext(Dispatchers.IO) {
                action()
                repository.getAll()
            }
        }
    }
}
