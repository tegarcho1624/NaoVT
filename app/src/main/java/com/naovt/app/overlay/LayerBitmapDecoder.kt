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

    /** Alto original (en píxeles) del PNG, sin cargarlo completo en memoria. */
    fun sourceHeight(resources: Resources, @DrawableRes resId: Int): Int {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
            inScaled = false
        }
        BitmapFactory.decodeResource(resources, resId, bounds)
        return bounds.outHeight
    }

    /** Mayor reducción (potencia de 2) que aún deja la imagen con al menos [targetHeightPx] de alto. */
    fun sampleSizeFor(sourceHeight: Int, targetHeightPx: Int): Int {
        var sampleSize = 1
        while (sourceHeight / (sampleSize * 2) >= targetHeightPx) sampleSize *= 2
        return sampleSize
    }

    fun decode(resources: Resources, @DrawableRes resId: Int, sampleSize: Int): Bitmap {
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inScaled = false // sin esto Android agranda la imagen según la densidad de pantalla
        }
        return BitmapFactory.decodeResource(resources, resId, options)
            ?: error("No se pudo decodificar el recurso $resId")
    }
}
