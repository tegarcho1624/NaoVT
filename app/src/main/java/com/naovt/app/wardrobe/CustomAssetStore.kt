package com.naovt.app.wardrobe

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import org.json.JSONArray
import java.io.File
import kotlin.math.abs

/** Resultado de importar una imagen al Armario. */
sealed interface ImportResult {
    /** [stretched]: la imagen no tiene la proporción 2:3 de las capas y se verá estirada. */
    data class Success(val fileName: String, val stretched: Boolean) : ImportResult
    data object Invalid : ImportResult
    data object Failed : ImportResult

    /** No se pudo conocer el nombre real de la imagen (la galería solo da un número): hay que pedirlo. */
    data object NeedsName : ImportResult
}

/**
 * Assets personalizados (importados por el usuario) y el orden de cada categoría.
 *
 * - Los archivos se copian a `filesDir/assets_personalizados/<categoría>/`.
 * - Qué archivos hay (registro) y el orden visual de cada categoría se guardan como JSON en
 *   SharedPreferences. El orden incluye también a los nativos, para poner los favoritos primero.
 *
 * Todo es bloqueante: se llama desde un hilo de fondo (importar) o es muy ligero (leer).
 */
class CustomAssetStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun directory(slot: WardrobeSlot) =
        File(appContext.filesDir, "$CUSTOM_ASSETS_DIR/${slot.customDirName}")

    private fun customKey(slot: WardrobeSlot) = "custom_${slot.customDirName}"
    private fun orderKey(slot: WardrobeSlot) = "order_${slot.name}"

    /** Nombres de archivo de los personalizados de [slot] que siguen existiendo. */
    fun customFiles(slot: WardrobeSlot): List<String> {
        val registered = readList(customKey(slot))
        val existing = registered.filter { File(directory(slot), it).isFile }
        if (existing.size != registered.size) writeList(customKey(slot), existing)
        return existing
    }

    /** Orden visual guardado de [slot]: identificadores de asset (nativos y personalizados). */
    fun order(slot: WardrobeSlot): List<String> = readList(orderKey(slot))

    fun saveOrder(slot: WardrobeSlot, ids: List<String>) = writeList(orderKey(slot), ids)

    /**
     * Copia la imagen elegida a la carpeta de [slot] y la registra. Solo acepta PNG y JPEG válidos.
     *
     * El asset se llama como el archivo original (sin la extensión .png/.jpg). Si la galería no
     * entrega un nombre real y no se da [name], devuelve [ImportResult.NeedsName] sin copiar nada.
     */
    fun import(slot: WardrobeSlot, uri: Uri, name: String? = null): ImportResult {
        val resolver = appContext.contentResolver
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            // decodeStream devuelve null en modo solo-medidas: por eso el bloque termina en `true`.
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds); true }
                ?: return ImportResult.Failed
            val extension = when (bounds.outMimeType) {
                "image/png" -> "png"
                "image/jpeg" -> "jpg"
                else -> return ImportResult.Invalid
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return ImportResult.Invalid

            val chosenName = name?.let { stripExtension(it).trim() }?.takeIf { it.isNotEmpty() }
                ?: realName(uri)
                ?: return ImportResult.NeedsName
            val dir = directory(slot).apply { mkdirs() }
            val target = uniqueFile(dir, safeBaseName(chosenName), extension)
            try {
                resolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } ?: return ImportResult.Failed
            } catch (e: Exception) {
                target.delete()
                throw e
            }

            writeList(customKey(slot), readList(customKey(slot)) + target.name)
            val ratio = bounds.outWidth.toFloat() / bounds.outHeight
            ImportResult.Success(target.name, stretched = abs(ratio - LAYER_RATIO) > RATIO_TOLERANCE)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo importar la imagen", e)
            ImportResult.Failed
        }
    }

    /** Borra el archivo personalizado y lo quita del registro y del orden. */
    fun delete(slot: WardrobeSlot, fileName: String) {
        File(directory(slot), fileName).delete()
        writeList(customKey(slot), readList(customKey(slot)) - fileName)
        writeList(orderKey(slot), readList(orderKey(slot)) - "$CUSTOM_ID_PREFIX$fileName")
    }

    /**
     * Nombre real del archivo elegido, sin extensión, o null si no se pudo conocer. La galería a veces
     * entrega solo un número (por ejemplo `1000064685.png`); en ese caso se prueban otras fuentes
     * (título de la imagen, última parte de la dirección) y, si todo es un número, se devuelve null.
     */
    private fun realName(uri: Uri): String? {
        val candidates = listOf(
            queryString(uri, OpenableColumns.DISPLAY_NAME),
            queryString(uri, TITLE_COLUMN),
            uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':'),
        )
        return candidates.firstNotNullOfOrNull { candidate ->
            candidate?.let { stripExtension(it).trim() }?.takeIf { isRealName(it) }
        }
    }

    private fun queryString(uri: Uri, column: String): String? = try {
        appContext.contentResolver.query(uri, arrayOf(column), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    } catch (e: Exception) {
        null
    }

    /** Un nombre real tiene algo más que números (los números solos son el ID de la galería). */
    private fun isRealName(name: String) = name.isNotBlank() && !name.all { it.isDigit() }

    /** Quita la extensión .png, .jpg o .jpeg (sin importar mayúsculas). */
    private fun stripExtension(name: String): String =
        name.replace(Regex("\\.(png|jpe?g)$", RegexOption.IGNORE_CASE), "")

    /**
     * Nombre seguro para un archivo: conserva letras (con acentos), números, espacios, guiones y guion
     * bajo, y quita lo que el sistema de archivos no permite.
     */
    private fun safeBaseName(name: String): String {
        val base = name
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]+"), "_")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '_', '.')
            .take(MAX_NAME_LENGTH)
        return base.ifEmpty { "imagen" }
    }

    /** `nombre.png`; si ya existe, `nombre_2.png`, `nombre_3.png`... */
    private fun uniqueFile(dir: File, base: String, extension: String): File {
        var candidate = File(dir, "$base.$extension")
        var n = 2
        while (candidate.exists()) {
            candidate = File(dir, "${base}_$n.$extension")
            n++
        }
        return candidate
    }

    private fun readList(key: String): List<String> {
        val json = prefs.getString(key, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            List(array.length()) { array.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writeList(key: String, values: List<String>) {
        prefs.edit().putString(key, JSONArray(values).toString()).apply()
    }

    private companion object {
        const val TAG = "CustomAssetStore"
        const val PREFS_NAME = "naovt_wardrobe_items"
        const val MAX_NAME_LENGTH = 60
        const val TITLE_COLUMN = "title"
        const val LAYER_RATIO = 1024f / 1536f
        const val RATIO_TOLERANCE = 0.01f
    }
}
