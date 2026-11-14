package com.naovt.app.wardrobe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Imágenes estáticas de los outfits: `filesDir/previews/<id del outfit>.png` (512x768, fondo
 * transparente). Se generan una sola vez, al guardar el outfit, y se reutilizan en la fila
 * "Mis Outfits" y en la pantalla de Gestos de Conjuntos. Así la imagen no cambia aunque después se
 * elimine un asset que el outfit usaba.
 */
object OutfitPreviews {

    private const val TAG = "OutfitPreviews"
    private const val DIR = "previews"

    private val cache = object : LruCache<String, ImageBitmap>((Runtime.getRuntime().maxMemory() / 24).toInt()) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.asAndroidBitmap().allocationByteCount
    }

    private fun file(context: Context, outfitId: String) =
        File(File(context.applicationContext.filesDir, DIR), "$outfitId.png")

    fun exists(context: Context, outfitId: String): Boolean = file(context, outfitId).isFile

    fun cached(outfitId: String): ImageBitmap? = cache.get(outfitId)

    /** Lee la imagen guardada del outfit, o null si no existe. */
    suspend fun load(context: Context, outfitId: String): ImageBitmap? {
        cache.get(outfitId)?.let { return it }
        return withContext(Dispatchers.IO) {
            val f = file(context, outfitId)
            if (!f.isFile) return@withContext null
            try {
                BitmapFactory.decodeFile(f.path)?.asImageBitmap()?.also { cache.put(outfitId, it) }
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo leer la imagen del outfit $outfitId", e)
                null
            }
        }
    }

    /** Genera y guarda la imagen del outfit a partir de sus capas (de atrás hacia adelante). */
    suspend fun save(context: Context, outfitId: String, layerPaths: List<String>): Boolean {
        val bitmap = WardrobeBitmaps.renderOutfitImage(context, layerPaths)
        return withContext(Dispatchers.IO) {
            try {
                val f = file(context, outfitId)
                f.parentFile?.mkdirs()
                f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                cache.put(outfitId, bitmap.asImageBitmap())
                true
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo guardar la imagen del outfit $outfitId", e)
                false
            }
        }
    }

    /** Borra la imagen del outfit (al eliminarlo). */
    fun delete(context: Context, outfitId: String) {
        cache.remove(outfitId)
        try {
            file(context, outfitId).delete()
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo borrar la imagen del outfit $outfitId", e)
        }
    }
}
