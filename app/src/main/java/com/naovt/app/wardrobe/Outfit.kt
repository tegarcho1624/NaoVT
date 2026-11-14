package com.naovt.app.wardrobe

import android.content.Context
import com.naovt.app.gestures.CharacterGestures
import com.naovt.app.gestures.GestureSettings
import org.json.JSONArray
import org.json.JSONObject

/**
 * Un conjunto guardado: el [name] elegido y la configuración de Nao en ese momento.
 *
 * - [selection]: archivo equipado en cada ranura (null = vacía), con los mismos identificadores que
 *   [CharacterConfig] (los personalizados llevan el prefijo `custom/`).
 * - [order]: orden de dibujo de las capas, de atrás hacia adelante.
 * - [gestures]: gestos propios de este outfit (pestañeo, hablar, beso). Si es null, Nao usa los
 *   globales del Menú de Gestos.
 */
data class Outfit(
    val id: String,
    val name: String,
    val selection: Map<WardrobeSlot, String?>,
    val order: List<WardrobeSlot>,
    val gestures: CharacterGestures? = null,
)

/** Lista de outfits guardados como JSON en SharedPreferences. */
class OutfitStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<Outfit> {
        val json = prefs.getString(KEY_OUTFITS, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { index ->
                try {
                    decode(array.getJSONObject(index))
                } catch (e: Exception) {
                    null // un outfit dañado no debe tirar los demás
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(outfits: List<Outfit>) {
        val array = JSONArray()
        outfits.forEach { array.put(encode(it)) }
        prefs.edit().putString(KEY_OUTFITS, array.toString()).apply()
    }

    private fun encode(outfit: Outfit) = JSONObject()
        .put("id", outfit.id)
        .put("name", outfit.name)
        .put("order", JSONArray(outfit.order.map { it.name }))
        .apply { outfit.gestures?.let { put("gestures", it.toJson()) } }
        .put(
            "items",
            JSONObject().apply {
                // "" = ranura vacía a propósito.
                outfit.selection.forEach { (slot, file) -> put(slot.name, file.orEmpty()) }
            },
        )

    private fun decode(json: JSONObject): Outfit {
        val items = json.getJSONObject("items")
        val selection = HashMap<WardrobeSlot, String?>()
        for (slot in WardrobeSlot.entries) {
            if (items.has(slot.name)) selection[slot] = items.getString(slot.name).ifEmpty { null }
        }
        val orderArray = json.optJSONArray("order") ?: JSONArray()
        val order = (0 until orderArray.length()).mapNotNull { i ->
            WardrobeSlot.entries.firstOrNull { it.name == orderArray.getString(i) }
        }
        return Outfit(
            id = json.getString("id"),
            name = json.getString("name"),
            selection = selection,
            order = order,
            gestures = json.optJSONObject("gestures")?.let { GestureSettings.fromJson(it) },
        )
    }

    private companion object {
        const val PREFS_NAME = "naovt_outfits"
        const val KEY_OUTFITS = "outfits"
    }
}
