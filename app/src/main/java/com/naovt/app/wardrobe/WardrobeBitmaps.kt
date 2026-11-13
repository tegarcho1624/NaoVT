package com.naovt.app.wardrobe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Tamaño del lienzo de todas las capas del personaje. */
const val LAYER_WIDTH = 1024
const val LAYER_HEIGHT = 1536

/**
 * Carga y guarda en memoria las imágenes de `assets/naomi/`.
 *
 * - Capas del avatar ([layer]): se decodifican a la resolución justa para el alto en pantalla.
 *   Las comparten la pantalla principal y la vista previa del Armario.
 * - Miniaturas del Armario ([thumbnail]): recortadas al contenido (una boca suelta ocuparía un
 *   puntito en el lienzo completo) y reducidas.
 *
 * Todo lo pesado corre en un hilo de fondo; las cachés se limitan a una fracción de la memoria.
 */
object WardrobeBitmaps {

    private const val TAG = "WardrobeBitmaps"
    private const val THUMB_MAX_SIDE = 320
    private const val THUMB_PADDING = 12
    private const val ALPHA_THRESHOLD = 12

    private val maxMemory = Runtime.getRuntime().maxMemory()

    private val layerCache = object : LruCache<String, ImageBitmap>((maxMemory / 4).toInt()) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.asAndroidBitmap().allocationByteCount
    }

    private val thumbCache = object : LruCache<String, ImageBitmap>((maxMemory / 16).toInt()) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.asAndroidBitmap().allocationByteCount
    }

    // Una miniatura a la vez: componer el lienzo completo (6 MB) en paralelo gastaría memoria de más.
    private val thumbLock = Mutex()

    /**
     * Mayor reducción (potencia de 2) que aún deja la capa con al menos [targetHeightPx] de alto.
     * Si Nao se dibuja chica, se gasta menos memoria; si es grande, no se ve borrosa.
     */
    fun sampleSizeFor(targetHeightPx: Int): Int {
        var sample = 1
        while (LAYER_HEIGHT / (sample * 2) >= targetHeightPx) sample *= 2
        return sample
    }

    /** Capa ya cargada, si está en caché (permite dibujarla en el primer fotograma). */
    fun cachedLayer(path: String, sampleSize: Int): ImageBitmap? = layerCache.get("$path@$sampleSize")

    /** Carga una capa del personaje. Devuelve null si el archivo no se puede leer. */
    suspend fun layer(context: Context, path: String, sampleSize: Int): ImageBitmap? {
        val key = "$path@$sampleSize"
        layerCache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            decode(context, path, sampleSize)?.asImageBitmap()?.also { layerCache.put(key, it) }
        }
    }

    fun cachedThumbnail(item: WardrobeItem): ImageBitmap? = thumbCache.get(item.assetPath)

    /**
     * Miniatura de [item]: su imagen (sobre el fondo de la ranura, si lo tiene) recortada al contenido.
     */
    suspend fun thumbnail(context: Context, item: WardrobeItem): ImageBitmap? {
        thumbCache.get(item.assetPath)?.let { return it }
        return thumbLock.withLock {
            val ready = thumbCache.get(item.assetPath)
            if (ready != null) {
                ready
            } else {
                withContext(Dispatchers.IO) {
                    buildThumbnail(context, item)?.asImageBitmap()?.also { thumbCache.put(item.assetPath, it) }
                }
            }
        }
    }

    private fun decode(context: Context, path: String, sampleSize: Int): Bitmap? = try {
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inScaled = false
        }
        context.applicationContext.assets.open(path).use { BitmapFactory.decodeStream(it, null, options) }
    } catch (e: Exception) {
        Log.w(TAG, "No se pudo cargar $path", e)
        null
    }

    private fun buildThumbnail(context: Context, item: WardrobeItem): Bitmap? {
        val source = decode(context, item.assetPath, 1) ?: return null

        // Fondo + imagen sobre un lienzo único (las capas del arte tienen tamaños de 1535 o 1536 de alto;
        // se estiran al lienzo estándar igual que al dibujar el avatar).
        val canvasBitmap = Bitmap.createBitmap(LAYER_WIDTH, LAYER_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(canvasBitmap)
        for (backdropPath in item.slot.backdrop) {
            decode(context, backdropPath, 1)?.let { drawFull(canvas, it) }
        }
        drawFull(canvas, source)

        val bounds = contentBounds(canvasBitmap)
        val result = if (bounds == null) {
            Bitmap.createScaledBitmap(canvasBitmap, LAYER_WIDTH / 4, LAYER_HEIGHT / 4, true)
        } else {
            val left = (bounds[0] - THUMB_PADDING).coerceAtLeast(0)
            val top = (bounds[1] - THUMB_PADDING).coerceAtLeast(0)
            val right = (bounds[2] + THUMB_PADDING).coerceAtMost(LAYER_WIDTH)
            val bottom = (bounds[3] + THUMB_PADDING).coerceAtMost(LAYER_HEIGHT)
            val cropped = Bitmap.createBitmap(canvasBitmap, left, top, right - left, bottom - top)
            val longSide = maxOf(cropped.width, cropped.height)
            if (longSide > THUMB_MAX_SIDE) {
                val scale = THUMB_MAX_SIDE.toFloat() / longSide
                Bitmap.createScaledBitmap(
                    cropped,
                    (cropped.width * scale).toInt().coerceAtLeast(1),
                    (cropped.height * scale).toInt().coerceAtLeast(1),
                    true,
                )
            } else {
                cropped
            }
        }
        if (result !== canvasBitmap) canvasBitmap.recycle()
        return result
    }

    private fun drawFull(canvas: Canvas, bitmap: Bitmap) {
        canvas.drawBitmap(
            bitmap,
            Rect(0, 0, bitmap.width, bitmap.height),
            Rect(0, 0, LAYER_WIDTH, LAYER_HEIGHT),
            null,
        )
        bitmap.recycle()
    }

    /** Rectángulo [izq, arriba, der, abajo] de los píxeles no transparentes, o null si todo es transparente. */
    private fun contentBounds(bitmap: Bitmap): IntArray? {
        val width = bitmap.width
        val height = bitmap.height
        val row = IntArray(width)
        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        for (y in 0 until height) {
            bitmap.getPixels(row, 0, width, 0, y, width, 1)
            for (x in 0 until width) {
                if ((row[x] ushr 24) > ALPHA_THRESHOLD) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    maxY = y
                }
            }
        }
        return if (maxX < 0) null else intArrayOf(minX, minY, maxX + 1, maxY + 1)
    }
}
