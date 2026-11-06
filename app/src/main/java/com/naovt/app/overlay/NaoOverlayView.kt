package com.naovt.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.ImageView
import com.naovt.app.R
import android.view.ViewGroup
import kotlin.math.hypot

/**
 * Nao flotante hecha con las mismas capas PNG del avatar (cuerpo, brazos, pecho, ojos, boca),
 * apiladas en ImageViews. Se usan Views clásicas (no Compose) porque la ventana flotante
 * vive en un Service, sin Activity.
 *
 * Gestos: toque = [onTap]; doble toque o mantener presionado = [onOpenApp]; arrastrar = mover.
 */
private const val MATCH_PARENT_PX = ViewGroup.LayoutParams.MATCH_PARENT

@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class NaoOverlayView(context: Context, heightPx: Int) : FrameLayout(context) {

    // Callbacks que conecta el servicio.
    var onTap: () -> Unit = {}
    var onOpenApp: () -> Unit = {}
    var positionProvider: () -> Point = { Point(0, 0) }
    var onMove: (x: Int, y: Int) -> Unit = { _, _ -> }
    var onMoveEnd: () -> Unit = {}

    private val eyesNormal: Bitmap
    private val eyesLoved: Bitmap
    private val mouthClosed: Bitmap
    private val mouthOpen: Bitmap
    private val eyesLayer: ImageView
    private val mouthLayer: ImageView

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
    private var lastTapUpTime = 0L
    private val longPressRunnable = Runnable {
        if (!dragging) {
            longPressed = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onOpenApp()
        }
    }

    init {
        fun bitmap(resId: Int) = LayerBitmapDecoder.decode(resources, resId, heightPx)
        fun layer(bitmap: Bitmap) = ImageView(context).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }.also { addView(it, FrameLayout.LayoutParams(MATCH_PARENT_PX, MATCH_PARENT_PX)) }

        eyesNormal = bitmap(R.drawable.ojos_base)
        eyesLoved = bitmap(R.drawable.ojos_corazon_mejilla)
        mouthClosed = bitmap(R.drawable.boca_base)
        mouthOpen = bitmap(R.drawable.boca_besando)

        // Mismo orden que NaoVTAvatar: cuerpo -> brazo izq. -> brazo der. -> pecho -> ojos -> boca.
        layer(bitmap(R.drawable.nao_base))
        layer(bitmap(R.drawable.nao_brazo_izquierdo_base))
        layer(bitmap(R.drawable.nao_brazo_derecho_base))
        layer(bitmap(R.drawable.pecho_base))
        eyesLayer = layer(eyesNormal)
        mouthLayer = layer(mouthClosed)

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

    /** Suelta las imágenes para liberar memoria. */
    fun release() {
        removeCallbacks(longPressRunnable)
        removeAllViews()
    }

    private fun refreshFace() {
        eyesLayer.setImageBitmap(if (loved) eyesLoved else eyesNormal)
        val mouthIsOpen = talkingMouthOpen ?: loved
        mouthLayer.setImageBitmap(if (mouthIsOpen) mouthOpen else mouthClosed)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                val position = positionProvider()
                startX = position.x
                startY = position.y
                dragging = false
                longPressed = false
                postDelayed(longPressRunnable, longPressMs)
            }

            MotionEvent.ACTION_MOVE -> {
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
}
