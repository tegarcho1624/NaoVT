package com.naovt.app.wardrobe

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel

/**
 * Guarda qué lleva puesto Nao ([config]). Pantalla principal y Armario piden este mismo ViewModel
 * (comparten la Activity), así que ven siempre el mismo atuendo y Nao no se "desviste" al cambiar de
 * pantalla. Además cada cambio se guarda en SharedPreferences: el atuendo sobrevive al cerrar la app.
 */
class WardrobeViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val catalog: WardrobeCatalog = WardrobeCatalog.load(application)

    var config by mutableStateOf(readSaved())
        private set

    /** Equipa [fileName] en [slot]; null deja la ranura vacía (solo si la ranura es opcional). */
    fun equip(slot: WardrobeSlot, fileName: String?) {
        if (fileName == null && !slot.optional) return
        if (fileName != null && !catalog.contains(slot, fileName)) return
        config = config.with(slot, fileName)
        // "" = ranura vacía a propósito (distinto de "nunca se eligió", que usa el atuendo inicial).
        prefs.edit().putString(slot.name, fileName.orEmpty()).apply()
    }

    private fun readSaved(): CharacterConfig {
        var result = CharacterConfig.Default
        for (slot in WardrobeSlot.entries) {
            val saved = prefs.getString(slot.name, null) ?: continue
            result = when {
                saved.isEmpty() && slot.optional -> result.with(slot, null)
                saved.isNotEmpty() && catalog.contains(slot, saved) -> result.with(slot, saved)
                else -> result // archivo que ya no existe: se queda el del atuendo inicial
            }
        }
        return result
    }

    private companion object {
        const val PREFS_NAME = "naovt_character"
    }
}
