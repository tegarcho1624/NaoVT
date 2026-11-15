package com.naovt.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Point
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.ui.graphics.asAndroidBitmap
import com.naovt.app.R
import com.naovt.app.wardrobe.CharacterConfig
import com.naovt.app.wardrobe.WardrobeBitmaps
import com.naovt.app.wardrobe.WardrobeSlot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.hypot

private const val MATCH_PARENT_PX = ViewGroup.LayoutParams.MATCH_PARENT

/**
 * Nao flotante hecha con el mismo sistema de capas que la pantalla principal: una ImageView por
 * ranura del Armario, en el orden de capas elegido, con ojos y boca según los gestos (pestañeo,
 * hablar, beso) del outfit puesto o los globales. Las imágenes salen de [WardrobeBitmaps], la misma
 * caché del avatar de la pantalla principal. Se usan Views clásicas (no Compose) porque la ventana flotante
 * vive en un Service, sin Activity.
 *
 * Gestos:
 * - Un toque: [onTap].
 * - Doble toque: [onOpenApp].
 * - Mantener presionado: [onLongPress] (abre el menú radial).
 * - Arrastrar con un dedo: [onMove].
 * - Pellizcar con dos dedos: [onScale] (factor de cambio) y [onScaleEnd] al soltar.
 *
 * Cuando hay dos dedos, el gesto se vuelve "solo pinch": no se mueve ni cuenta como toque,
 * hasta que se levanten todos los dedos.
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
internal class NaoOverlayView(
    context: Context,
    initialHeightPx: Int,
    initialLook: OverlayLook,
) : FrameLayout(context) {

    // Callbacks que conecta el servicio.
    var onTap: () -> Unit = {}
    var onOpenApp: () -> Unit = {}
    var onLongPress: () -> Unit = {}
    var positionProvider: () -> Point = { Point(0, 0) }
    var onMove: (x: Int, y: Int) -> Unit = { _, _ -> }
    var onMoveEnd: () -> Unit = {}
    var onScale: (factor: Float) -> Unit = {}
    var onScaleEnd: () -> Unit = {}

    // Capas: una ImageView por ranura, apiladas según config.order.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val slotViews = HashMap<WardrobeSlot, ImageView>()
    private val shownPaths = HashMap<WardrobeSlot, String?>()
    private val loadJobs = HashMap<WardrobeSlot, Job>()
    private var config: CharacterConfig = initialLook.config
    private var gestures = initialLook.gestures
    private var sampleSize = WardrobeBitmaps.sampleSizeFor(initialHeightPx)

    private var loved = false
    private var talkingMouthOpen: Boolean? = null
    private var blinking = false

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
            onLongPress()
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
        contentDescription = context.getString(R.string.nao_description)
        rebuildLayers()
    }

    /** Cambia la ropa/orden de capas y los gestos (por ejemplo, tras cambiar de outfit en la app). */
    fun setLook(look: OverlayLook) {
        val orderChanged = look.config.order != config.order
        config = look.config
        gestures = look.gestures
        if (orderChanged) rebuildLayers() else refreshLayers()
    }

    /** Cara de cariño (ojos y boca del gesto Beso) activada o desactivada. */
    fun setLoved(value: Boolean) {
        loved = value
        refreshLayers()
    }

    /** Parpadeo: ojos y boca del gesto Pestañeo (solo se nota si Nao está en estado base). */
    fun setBlinking(value: Boolean) {
        blinking = value
        refreshLayers()
    }

    /** Hablando: true/false alterna boca abierta/cerrada (gesto Hablar); null = no está hablando. */
    fun setTalkingMouth(open: Boolean?) {
        talkingMouthOpen = open
        refreshLayers()
    }

    /**
     * Ajusta la calidad de las imágenes al alto actual de la ventana. Solo vuelve a cargar
     * si el tamaño cambió lo suficiente.
     */
    fun updateResolution(targetHeightPx: Int) {
        val newSampleSize = WardrobeBitmaps.sampleSizeFor(targetHeightPx)
        if (newSampleSize == sampleSize) return
        sampleSize = newSampleSize
        shownPaths.clear()
        refreshLayers()
    }

    /** Suelta las imágenes para liberar memoria. */
    fun release() {
        removeCallbacks(longPressRunnable)
        scope.cancel()
        removeAllViews()
        slotViews.clear()
        shownPaths.clear()
        loadJobs.clear()
    }

    private fun rebuildLayers() {
        loadJobs.values.forEach { it.cancel() }
        loadJobs.clear()
        removeAllViews()
        slotViews.clear()
        shownPaths.clear()
        for (slot in config.order) {
            val layer = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_XY }
            slotViews[slot] = layer
            addView(layer, FrameLayout.LayoutParams(MATCH_PARENT_PX, MATCH_PARENT_PX))
        }
        refreshLayers()
    }

    /** Ojos y boca que se ven ahora. Prioridad (igual que la pantalla principal): cariño > hablando > parpadeo > Armario. */
    private fun shownConfig(): CharacterConfig {
        val wardrobeEyes = config.fileFor(WardrobeSlot.Eyes)
        val wardrobeMouth = config.fileFor(WardrobeSlot.Mouth)
        val talking = talkingMouthOpen
        val eyes = when {
            loved -> gestures.kissEyes ?: wardrobeEyes
            talking != null -> gestures.talkEyes ?: wardrobeEyes
            blinking -> gestures.blinkEyes ?: wardrobeEyes
            else -> wardrobeEyes
        }
        val mouth = when {
            talking != null -> if (talking) gestures.talkMouth ?: wardrobeMouth else wardrobeMouth
            loved -> gestures.kissMouth ?: wardrobeMouth
            blinking -> gestures.blinkMouth ?: wardrobeMouth
            else -> wardrobeMouth
        }
        return config.with(WardrobeSlot.Eyes, eyes).with(WardrobeSlot.Mouth, mouth)
    }

    /** Solo vuelve a cargar las capas cuyo archivo cambió (normalmente solo ojos y boca). */
    private fun refreshLayers() {
        val shown = shownConfig()
        for ((slot, layer) in slotViews) {
            val path = shown.assetPathFor(slot)
            if (shownPaths.containsKey(slot) && shownPaths[slot] == path) continue
            shownPaths[slot] = path
            showLayer(slot, layer, path, fallbackPath = config.assetPathFor(slot))
        }
    }

    private fun showLayer(slot: WardrobeSlot, layer: ImageView, path: String?, fallbackPath: String?) {
        loadJobs.remove(slot)?.cancel()
        if (path == null) {
            layer.setImageBitmap(null)
            return
        }
        val size = sampleSize
        val cached = WardrobeBitmaps.cachedLayer(path, size)
        if (cached != null) {
            layer.setImageBitmap(cached.asAndroidBitmap())
            return
        }
        // La imagen anterior se queda a la vista hasta que la nueva esté lista (sin huecos al parpadear).
        loadJobs[slot] = scope.launch {
            // Si el asset de un gesto ya no existe (uno importado y eliminado), se usa el del Armario.
            val bitmap = WardrobeBitmaps.layer(context, path, size)
                ?: fallbackPath?.takeIf { it != path }?.let { WardrobeBitmaps.layer(context, it, size) }
            if (bitmap != null) layer.setImageBitmap(bitmap.asAndroidBitmap())
        }
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
                // Tras un pellizco o un mantener presionado ya no se arrastra.
                if (multiTouch || longPressed) return true
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
                    longPressed -> Unit // ya abrió el menú
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
