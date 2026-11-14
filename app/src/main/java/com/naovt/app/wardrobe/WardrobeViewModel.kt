package com.naovt.app.wardrobe

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.naovt.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Guarda qué lleva puesto Nao ([config]) y administra los assets de cada categoría: los nativos de la
 * app más los que importa el usuario, en el orden que él prefiera ([itemsFor]).
 *
 * Pantalla principal y Armario piden este mismo ViewModel (comparten la Activity), así que ven siempre
 * el mismo atuendo y Nao no se "desviste" al cambiar de pantalla. Cada cambio se guarda en
 * SharedPreferences: el atuendo, el orden de las capas, los assets importados y su orden sobreviven
 * al cerrar la app.
 */
class WardrobeViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val store = CustomAssetStore(application)
    private val outfitStore = OutfitStore(application)

    /** Assets nativos (los de `assets/naomi/`). Nunca cambian ni se pueden borrar. */
    private val nativeCatalog: WardrobeCatalog = WardrobeCatalog.load(application)

    private var customItems by mutableStateOf(loadCustomItems())
    private var savedOrders by mutableStateOf(loadOrders())

    var config by mutableStateOf(readSaved())
        private set

    /** Outfits guardados, en el orden en que se crearon. */
    var outfits by mutableStateOf(outfitStore.load())
        private set

    /** Aviso para mostrar al usuario (un Toast); la pantalla lo muestra y llama a [clearMessage]. */
    /** Id de string del aviso (R.string.*). */
    var message by mutableStateOf<Int?>(null)
        private set

    fun clearMessage() {
        message = null
    }

    /**
     * Assets de [slot] en el orden elegido por el usuario: primero los que él ordenó; los nuevos
     * (por ejemplo, uno recién importado) van al final.
     */
    fun itemsFor(slot: WardrobeSlot): List<WardrobeItem> {
        val all = nativeCatalog.itemsFor(slot) + customItems[slot].orEmpty()
        val saved = savedOrders[slot].orEmpty()
        if (saved.isEmpty()) return all
        val byId = all.associateBy { it.fileName }
        val savedSet = saved.toSet()
        return saved.mapNotNull { byId[it] } + all.filter { it.fileName !in savedSet }
    }

    private fun contains(slot: WardrobeSlot, id: String): Boolean =
        nativeCatalog.contains(slot, id) || customItems[slot].orEmpty().any { it.fileName == id }

    /** Equipa [fileName] en [slot]; null deja la ranura vacía (solo si la ranura es opcional). */
    fun equip(slot: WardrobeSlot, fileName: String?) {
        if (fileName == null && !slot.optional) return
        if (fileName != null && !contains(slot, fileName)) return
        config = config.with(slot, fileName)
        // "" = ranura vacía a propósito (distinto de "nunca se eligió", que usa el atuendo inicial).
        prefs.edit().putString(slot.name, fileName.orEmpty()).apply()
    }

    /**
     * Gestor de Capas: guarda el nuevo orden de dibujo de las ranuras equipadas
     * ([visibleBottomToTop]: de la capa de más atrás a la de más adelante).
     */
    fun reorder(visibleBottomToTop: List<WardrobeSlot>) {
        val updated = config.withVisibleOrder(visibleBottomToTop)
        if (updated == config) return
        config = updated
        prefs.edit().putString(KEY_ORDER, updated.order.joinToString(",") { it.name }).apply()
    }

    /** Guarda el atuendo actual (assets de cada categoría y orden de capas) con el nombre [name]. */
    fun saveOutfit(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        val outfit = Outfit(UUID.randomUUID().toString(), clean, config.selections(), config.order)
        outfits = outfits + outfit
        outfitStore.save(outfits)
    }

    /** Reordena los outfits según [ids] (los mismos outfits en el orden nuevo) y guarda el orden. */
    fun moveOutfits(ids: List<String>) {
        val byId = outfits.associateBy { it.id }
        val reordered = ids.mapNotNull { byId[it] }
        // Si la lista no trae exactamente los mismos outfits, no se toca nada.
        if (reordered.size != outfits.size || reordered == outfits) return
        outfits = reordered
        outfitStore.save(outfits)
    }

    fun deleteOutfit(outfit: Outfit) {
        outfits = outfits.filter { it.id != outfit.id }
        outfitStore.save(outfits)
    }

    /**
     * Configuración que resulta de [outfit] con lo que existe hoy. Si un asset del outfit ya no está
     * (por ejemplo, un personalizado eliminado después) se ignora: la ranura queda vacía, o con el
     * asset inicial si no puede quedar vacía. Nunca falla.
     */
    fun resolveOutfit(outfit: Outfit): CharacterConfig {
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

    /** Viste a Nao con [outfit] y deja ese atuendo guardado como el actual. */
    fun applyOutfit(outfit: Outfit) {
        val resolved = resolveOutfit(outfit)
        config = resolved
        prefs.edit().apply {
            for (slot in WardrobeSlot.entries) putString(slot.name, resolved.fileFor(slot).orEmpty())
            putString(KEY_ORDER, resolved.order.joinToString(",") { it.name })
        }.apply()
    }

    /**
     * Cambia el orden VISUAL de los assets de [slot] en la cuadrícula ([ids] en el orden nuevo).
     * No afecta al orden de las capas de Nao.
     */
    fun moveAssets(slot: WardrobeSlot, ids: List<String>) {
        if (ids == itemsFor(slot).map { it.fileName }) return
        store.saveOrder(slot, ids)
        savedOrders = savedOrders + (slot to ids)
    }

    /** Importa la imagen elegida en el selector a la categoría [slot]. */
    fun importAsset(slot: WardrobeSlot, uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { store.import(slot, uri) }
            when (result) {
                is ImportResult.Success -> {
                    customItems = customItems + (slot to loadCustomItems(slot))
                    if (result.stretched) message = R.string.wardrobe_import_stretched
                }
                ImportResult.Invalid -> message = R.string.wardrobe_import_invalid
                ImportResult.Failed -> message = R.string.wardrobe_import_failed
            }
        }
    }

    /**
     * Elimina un asset personalizado (archivo, registro y orden). Los nativos no se pueden eliminar.
     * Si lo llevaba puesto Nao, se lo quita (o vuelve al asset inicial si la ranura no puede quedar vacía).
     */
    fun deleteAsset(item: WardrobeItem) {
        if (!item.isCustom) return
        val slot = item.slot
        val wasEquipped = config.fileFor(slot) == item.fileName

        store.delete(slot, item.fileName.removePrefix(CUSTOM_ID_PREFIX))
        WardrobeBitmaps.evict(item.assetPath)
        customItems = customItems + (slot to loadCustomItems(slot))
        savedOrders = savedOrders + (slot to store.order(slot))

        if (wasEquipped) equip(slot, if (slot.optional) null else CharacterConfig.Default.fileFor(slot))
    }

    private fun loadCustomItems(slot: WardrobeSlot): List<WardrobeItem> =
        store.customFiles(slot).map { name ->
            WardrobeItem(
                slot = slot,
                fileName = "$CUSTOM_ID_PREFIX$name",
                assetPath = "$CUSTOM_ASSETS_DIR/${slot.customDirName}/$name",
            )
        }

    private fun loadCustomItems(): Map<WardrobeSlot, List<WardrobeItem>> =
        WardrobeSlot.entries.associateWith { loadCustomItems(it) }

    private fun loadOrders(): Map<WardrobeSlot, List<String>> =
        WardrobeSlot.entries.associateWith { store.order(it) }

    private fun readSaved(): CharacterConfig {
        var result = CharacterConfig.Default
        for (slot in WardrobeSlot.entries) {
            val saved = prefs.getString(slot.name, null) ?: continue
            result = when {
                saved.isEmpty() && slot.optional -> result.with(slot, null)
                saved.isNotEmpty() && contains(slot, saved) -> result.with(slot, saved)
                else -> result // archivo que ya no existe: se queda el del atuendo inicial
            }
        }
        val savedOrder = prefs.getString(KEY_ORDER, null)
            ?.split(',')
            ?.mapNotNull { name -> WardrobeSlot.entries.firstOrNull { it.name == name } }
        return if (savedOrder.isNullOrEmpty()) result else result.withOrder(savedOrder)
    }

    private companion object {
        const val PREFS_NAME = "naovt_character"
        const val KEY_ORDER = "layer_order"
    }
}
