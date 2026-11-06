package com.naovt.app.overlay

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes

/**
 * Decodifica las capas PNG (1024x1536) a un tamaño reducido para la burbuja,
 * así el overlay usa poca memoria en vez de cargar las imágenes completas.
 */
internal object LayerBitmapDecoder {

    fun decode(resources: Resources, @DrawableRes resId: Int, targetHeightPx: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
            inScaled = false
        }
        BitmapFactory.decodeResource(resources, resId, bounds)

        var sampleSize = 1
        while (bounds.outHeight / (sampleSize * 2) >= targetHeightPx) sampleSize *= 2

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inScaled = false // sin esto Android agranda la imagen según la densidad de pantalla
        }
        return BitmapFactory.decodeResource(resources, resId, options)
            ?: error("No se pudo decodificar el recurso $resId")
    }
}
