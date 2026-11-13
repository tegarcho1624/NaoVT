package com.naovt.app.wardrobe

import android.content.Context
import android.util.Log

/**
 * Catálogo de todo lo que Nao puede ponerse. Se arma leyendo las carpetas de `assets/naomi/`
 * (las mismas del ZIP de arte): cada [WardrobeSlot] toma los PNG de su carpeta.
 *
 * Agregar arte nuevo no requiere tocar código: basta con poner el PNG en la carpeta correcta.
 * Las carpetas que no corresponden a ninguna ranura se ignoran.
 */
class WardrobeCatalog private constructor(private val bySlot: Map<WardrobeSlot, List<WardrobeItem>>) {

    /** Assets disponibles para [slot], ordenados por nombre. */
    fun itemsFor(slot: WardrobeSlot): List<WardrobeItem> = bySlot[slot].orEmpty()

    /** ¿Existe [fileName] en [slot]? Sirve para descartar selecciones guardadas de archivos que ya no están. */
    fun contains(slot: WardrobeSlot, fileName: String): Boolean =
        itemsFor(slot).any { it.fileName == fileName }

    companion object {
        private const val TAG = "WardrobeCatalog"

        fun load(context: Context): WardrobeCatalog {
            val assets = context.applicationContext.assets
            val bySlot = WardrobeSlot.entries.associateWith { slot ->
                val dir = "$WARDROBE_ASSETS_ROOT/${slot.folder}"
                val names = try {
                    assets.list(dir).orEmpty()
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo leer $dir", e)
                    emptyArray()
                }
                names
                    .filter { it.endsWith(".png", ignoreCase = true) }
                    .filter { slot.nameFilter == null || it.contains(slot.nameFilter, ignoreCase = true) }
                    .sorted()
                    .map { WardrobeItem(slot, it, "$dir/$it") }
            }
            return WardrobeCatalog(bySlot)
        }
    }
}
