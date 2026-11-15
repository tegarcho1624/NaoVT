package com.naovt.app.ui.room

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Decodifica la imagen de fondo de una habitación importada. Bloqueante: usar fuera del hilo principal. */
internal object RoomImageLoader {

    private const val TAG = "RoomImageLoader"

    /**
     * Carga [path] con la rotación EXIF ya aplicada. Se reduce (potencia de 2) solo mientras la imagen
     * siga cubriendo la pantalla ([screenWidth] x [screenHeight]) en los dos ejes, para no gastar
     * memoria de más ni verse borrosa. Devuelve null si no se puede leer.
     */
    fun load(path: String, screenWidth: Int, screenHeight: Int): ImageBitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            val orientation = exifOrientation(path)
            val rotated = orientation in ROTATED_90
            // Medidas ya con la rotación, que son las que se ven.
            val width = if (rotated) bounds.outHeight else bounds.outWidth
            val height = if (rotated) bounds.outWidth else bounds.outHeight
            var sample = 1
            while (width / (sample * 2) >= screenWidth && height / (sample * 2) >= screenHeight) sample *= 2

            val decoded = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            decoded?.let { applyOrientation(it, orientation).asImageBitmap() }
        }
    } catch (e: Exception) {
        Log.w(TAG, "No se pudo leer la imagen de fondo", e)
        null
    } catch (e: OutOfMemoryError) {
        Log.w(TAG, "Sin memoria para la imagen de fondo", e)
        null
    }

    private val ROTATED_90 = setOf(
        ExifInterface.ORIENTATION_ROTATE_90,
        ExifInterface.ORIENTATION_ROTATE_270,
        ExifInterface.ORIENTATION_TRANSPOSE,
        ExifInterface.ORIENTATION_TRANSVERSE,
    )

    private fun exifOrientation(path: String): Int = try {
        ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } catch (e: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }

    private fun applyOrientation(source: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            else -> return source
        }
        val result = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (result !== source) source.recycle()
        return result
    }
}
