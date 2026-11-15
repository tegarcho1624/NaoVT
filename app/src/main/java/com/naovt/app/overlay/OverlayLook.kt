package com.naovt.app.overlay

import android.content.Context
import com.naovt.app.gestures.GestureSettings
import com.naovt.app.gestures.GesturesViewModel
import com.naovt.app.wardrobe.CUSTOM_ID_PREFIX
import com.naovt.app.wardrobe.CharacterConfig
import com.naovt.app.wardrobe.CustomAssetStore
import com.naovt.app.wardrobe.Outfit
import com.naovt.app.wardrobe.OutfitStore
import com.naovt.app.wardrobe.WardrobeCatalog
import com.naovt.app.wardrobe.WardrobeSlot

/** Cómo se ve Nao ahora mismo: su ropa/capas y los gestos que le tocan (los de su outfit o los globales). */
internal data class OverlayLook(val config: CharacterConfig, val gestures: GestureSettings)

/**
 * Lee de SharedPreferences lo mismo que usa la pantalla principal (Armario, outfits y Menú de Gestos),
 * porque Nao flotante vive en un Service y no tiene los ViewModels de la Activity. La lógica es la
 * misma que la de `WardrobeViewModel` (`readSaved`, `resolveOutfit`, `activeOutfit`).
 */
internal object OverlayLookLoader {

    private const val CHARACTER_PREFS = "naovt_character"
    private const val KEY_ORDER = "layer_order"
    private const val KEY_LAST_OUTFIT = "last_outfit_id"

    /** Archivos de preferencias cuyo cambio puede cambiar el aspecto de Nao flotante. */
    val WATCHED_PREFS = listOf(CHARACTER_PREFS, "naovt_outfits", "naovt_gestures", "naovt_wardrobe_items")

    fun load(context: Context): OverlayLook {
        val app = context.applicationContext
        val catalog = WardrobeCatalog.load(app)
        val customStore = CustomAssetStore(app)
        val customIds = WardrobeSlot.entries.associateWith { slot ->
            customStore.customFiles(slot).map { "$CUSTOM_ID_PREFIX$it" }.toSet()
        }
        fun contains(slot: WardrobeSlot, id: String) =
            catalog.contains(slot, id) || id in customIds[slot].orEmpty()

        val prefs = app.getSharedPreferences(CHARACTER_PREFS, Context.MODE_PRIVATE)
        val config = readSaved(prefs, ::contains)

        val matches = OutfitStore(app).load().filter { resolve(it, ::contains) == config }
        val lastId = prefs.getString(KEY_LAST_OUTFIT, null)
        val active = matches.firstOrNull { it.id == lastId } ?: matches.firstOrNull()
        return OverlayLook(config, active?.gestures ?: GesturesViewModel.load(app))
    }

    private fun readSaved(
        prefs: android.content.SharedPreferences,
        contains: (WardrobeSlot, String) -> Boolean,
    ): CharacterConfig {
        var result = CharacterConfig.Default
        for (slot in WardrobeSlot.entries) {
            val saved = prefs.getString(slot.name, null) ?: continue
            result = when {
                saved.isEmpty() && slot.optional -> result.with(slot, null)
                saved.isNotEmpty() && contains(slot, saved) -> result.with(slot, saved)
                else -> result
            }
        }
        val savedOrder = prefs.getString(KEY_ORDER, null)
            ?.split(',')
            ?.mapNotNull { name -> WardrobeSlot.entries.firstOrNull { it.name == name } }
        return if (savedOrder.isNullOrEmpty()) result else result.withOrder(savedOrder)
    }

    private fun resolve(outfit: Outfit, contains: (WardrobeSlot, String) -> Boolean): CharacterConfig {
        var result = CharacterConfig.Default
        for (slot in WardrobeSlot.entries) {
            val saved = outfit.selection[slot]
            val file = when {
                saved != null && contains(slot, saved) -> saved
                slot.optional -> null
                else -> CharacterConfig.Default.fileFor(slot)
            }
            result = result.with(slot, file)
        }
        return result.withOrder(outfit.order)
    }
}
