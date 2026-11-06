package com.naovt.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import com.naovt.app.R
import kotlin.math.hypot

private const val MATCH_PARENT_PX = ViewGroup.LayoutParams.MATCH_PARENT

/**
 * Nao flotante hecha con las mismas capas PNG del avatar (cuerpo, brazos, pecho, ojos, boca),
 * apiladas en ImageViews. Se usan Views clásicas (no Compose) porque la ventana flotante
 * vive en un Service, sin Activity.
 *
 * Gestos:
 * - Un toque: [onTap].
 * - Doble toque o mantener presionado: [onOpenApp].
 * - Arrastrar con un dedo: [onMove].
 * - Pellizcar con dos dedos: [onScale] (factor de cambio) y [onScaleEnd] al soltar.
 *
 * Cuando hay dos dedos, el gesto se vuelve "solo pinch": no se mueve ni cuenta como toque,
 * hasta que se levanten todos los dedos.
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class NaoOverlayView(context: Context, initialHeightPx: Int) : FrameLayout(context) {

    // Callbacks que conecta el servicio.
    var onTap: () -> Unit = {}
    var onOpenApp: () -> Unit = {}
    var positionProvider: () -> Point = { Point(0, 0) }
    var onMove: (x: Int, y: Int) -> Unit = { _, _ -> }
    var onMoveEnd: () -> Unit = {}
    var onScale: (factor: Float) -> Unit = {}
    var onScaleEnd: () -> Unit = {}

    // Imágenes: se decodifican a la resolución justa y se vuelven a decodificar si el tamaño cambia mucho.
    private val bitmaps = HashMap<Int, Bitmap>()
    private val sourceHeight = LayerBitmapDecoder.sourceHeight(resources, R.drawable.nao_base)
    private var sampleSize = 0

    private val baseLayer = newLayer()
    private val leftArmLayer = newLayer()
    private val rightArmLayer = newLayer()
    private val chestLayer = newLayer()
    private val eyesLayer = newLayer()
    private val mouthLayer = newLayer()

    private var loved = false
    private var talkingMouthOpen: Boolean? = null

    // Gestos
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressMs = ViewConfiguration.getLongPressTimeout().toLong()
    private val doubleTapMs = ViewConfiguration.getDoubleTapTimeout().toLong()
    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0
    private var dragging = false
    private var longPressed = false
    private var multiTouch = false
    private var lastTapUpTime = 0L
    private val longPressRunnable = Runnable {
        if (!dragging && !multiTouch) {
            longPressed = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onOpenApp()
        }
    }

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                this@NaoOverlayView.onScale(detector.scaleFactor)
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                this@NaoOverlayView.onScaleEnd()
            }
        },
    ).apply {
        // Sin esto, "doble toque y arrastrar" con un dedo también escalaría y chocaría
        // con el doble toque que abre la app.
        isQuickScaleEnabled = false
        isStylusScaleEnabled = false
    }

    init {
        updateResolution(initialHeightPx)
        contentDescription = context.getString(R.string.nao_description)
    }

    /** Cara de cariño (ojos corazón + boca de beso) activada o desactivada. */
    fun setLoved(value: Boolean) {
        loved = value
        refreshFace()
    }

    /** Boca al hablar: true/false alterna abierta/cerrada; null = la boca de la expresión actual. */
    fun setTalkingMouth(open: Boolean?) {
        talkingMouthOpen = open
        refreshFace()
    }

    /**
     * Ajusta la calidad de las imágenes al alto actual de la ventana. Solo vuelve a decodificar
     * si el tamaño cambió lo suficiente; así no se gasta memoria de más cuando Nao es pequeña
     * ni se ve borrosa cuando es grande.
     */
    fun updateResolution(targetHeightPx: Int) {
        val newSampleSize = LayerBitmapDecoder.sampleSizeFor(sourceHeight, targetHeightPx)
        if (newSampleSize == sampleSize) return
        sampleSize = newSampleSize

        for (resId in LAYER_RESOURCES) {
            bitmaps[resId] = LayerBitmapDecoder.decode(resources, resId, newSampleSize)
        }
        baseLayer.setImageBitmap(bitmaps.getValue(R.drawable.nao_base))
        leftArmLayer.setImageBitmap(bitmaps.getValue(R.drawable.nao_brazo_izquierdo_base))
        rightArmLayer.setImageBitmap(bitmaps.getValue(R.drawable.nao_brazo_derecho_base))
        chestLayer.setImageBitmap(bitmaps.getValue(R.drawable.pecho_base))
        refreshFace()
    }

    /** Suelta las imágenes para liberar memoria. */
    fun release() {
        removeCallbacks(longPressRunnable)
        removeAllViews()
        bitmaps.clear()
    }

    private fun newLayer(): ImageView =
        ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
            .also { addView(it, FrameLayout.LayoutParams(MATCH_PARENT_PX, MATCH_PARENT_PX)) }

    private fun refreshFace() {
        val eyes = if (loved) R.drawable.ojos_corazon_mejilla else R.drawable.ojos_base
        val mouthIsOpen = talkingMouthOpen ?: loved
        val mouth = if (mouthIsOpen) R.drawable.boca_besando else R.drawable.boca_base
        bitmaps[eyes]?.let { eyesLayer.setImageBitmap(it) }
        bitmaps[mouth]?.let { mouthLayer.setImageBitmap(it) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                val position = positionProvider()
                startX = position.x
                startY = position.y
                dragging = false
                longPressed = false
                multiTouch = false
                postDelayed(longPressRunnable, longPressMs)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // Segundo dedo: pasa a ser un pellizco. Se cancela cualquier toque o arrastre.
                removeCallbacks(longPressRunnable)
                if (dragging) {
                    dragging = false
                    onMoveEnd()
                }
                multiTouch = true
            }

            MotionEvent.ACTION_MOVE -> {
                if (multiTouch) return true
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (!dragging && hypot(dx, dy) > touchSlop) {
                    dragging = true
                    removeCallbacks(longPressRunnable)
                }
                if (dragging) onMove(startX + dx.toInt(), startY + dy.toInt())
            }

            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                when {
                    multiTouch -> Unit // terminó un pellizco: no es un toque
                    dragging -> onMoveEnd()
                    longPressed -> Unit // ya abrió la app
                    else -> handleTap(event.eventTime)
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                if (dragging) onMoveEnd()
            }
        }
        return true
    }

    private fun handleTap(time: Long) {
        if (time - lastTapUpTime <= doubleTapMs) {
            lastTapUpTime = 0L
            onOpenApp()
        } else {
            lastTapUpTime = time
            onTap()
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(longPressRunnable)
        super.onDetachedFromWindow()
    }

    private companion object {
        val LAYER_RESOURCES = intArrayOf(
            R.drawable.nao_base,
            R.drawable.nao_brazo_izquierdo_base,
            R.drawable.nao_brazo_derecho_base,
            R.drawable.pecho_base,
            R.drawable.ojos_base,
            R.drawable.ojos_corazon_mejilla,
            R.drawable.boca_base,
            R.drawable.boca_besando,
        )
    }
}
