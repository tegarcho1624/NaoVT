package com.naovt.app.alerts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import com.naovt.app.R
import com.naovt.app.overlay.LayerBitmapDecoder
import com.naovt.app.ui.avatar.NaoExpression

/** La carita de Nao (recorte circular) con la expresión de la alerta, para la notificación. */
internal object AlertFaceBitmap {

    private const val SAMPLE_SIZE = 2 // 1024x1536 -> 512x768
    private const val OUTPUT_PX = 192

    // Zona de la cara en el PNG original (la misma que usa NaoHeadBadge).
    private const val HEAD_LEFT = 242
    private const val HEAD_TOP = 0
    private const val HEAD_SIZE = 540

    fun build(context: Context, expression: NaoExpression): Bitmap? = try {
        val output = Bitmap.createBitmap(OUTPUT_PX, OUTPUT_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.clipPath(
            Path().apply { addCircle(OUTPUT_PX / 2f, OUTPUT_PX / 2f, OUTPUT_PX / 2f, Path.Direction.CW) }
        )
        canvas.drawColor(BACKGROUND_COLOR)

        val source = Rect(
            HEAD_LEFT / SAMPLE_SIZE,
            HEAD_TOP / SAMPLE_SIZE,
            (HEAD_LEFT + HEAD_SIZE) / SAMPLE_SIZE,
            (HEAD_TOP + HEAD_SIZE) / SAMPLE_SIZE,
        )
        val destination = Rect(0, 0, OUTPUT_PX, OUTPUT_PX)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)

        val layers = intArrayOf(
            R.drawable.nao_base,
            R.drawable.pecho_base,
            expression.eyes,
            expression.mouth.drawable,
        )
        for (resId in layers) {
            val layer = LayerBitmapDecoder.decode(context.resources, resId, SAMPLE_SIZE)
            canvas.drawBitmap(layer, source, destination, paint)
            layer.recycle()
        }
        output
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    private val BACKGROUND_COLOR = 0xFF1B1820.toInt()
}
