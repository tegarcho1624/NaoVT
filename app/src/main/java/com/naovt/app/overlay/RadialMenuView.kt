package com.naovt.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Una opción del menú radial. Para agregar una nueva, solo se suma una a la lista del servicio. */
data class RadialMenuItem(
    val id: String,
    @DrawableRes val icon: Int,
    @StringRes val label: Int,
    val onClick: () -> Unit,
)

/**
 * Menú circular alrededor de Nao. Ocupa toda la pantalla con un fondo oscuro translúcido:
 * tocar fuera de los botones lo cierra ([onDismiss]).
 */
@SuppressLint("ViewConstructor")
class RadialMenuView(
    context: Context,
    items: List<RadialMenuItem>,
    private val centerX: Float,
    private val centerY: Float,
    private val radiusPx: Float,
    private val buttonSizePx: Int,
    private val onDismiss: () -> Unit,
) : FrameLayout(context) {

    private val density = resources.displayMetrics.density

    init {
        setBackgroundColor(DIM_COLOR)
        setOnClickListener { onDismiss() } // tocar fuera de los botones cierra el menú
        items.forEachIndexed { index, item -> addItemView(item, index, items.size) }
    }

    private fun addItemView(item: RadialMenuItem, index: Int, count: Int) {
        // El primer botón queda arriba y el resto sigue en el sentido del reloj.
        val angle = -PI / 2 + 2 * PI * index / count
        val x = centerX + radiusPx * cos(angle).toFloat()
        val y = centerY + radiusPx * sin(angle).toFloat()

        val labelText = context.getString(item.label)
        val containerWidth = (buttonSizePx * 1.7f).toInt()

        val iconView = ImageView(context).apply {
            setImageResource(item.icon)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val padding = (buttonSizePx * 0.24f).toInt()
            setPadding(padding, padding, padding, padding)
            if (TINT_ICONS) setColorFilter(ICON_TINT)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(BUTTON_COLOR)
                setStroke((2 * density).toInt(), STROKE_COLOR)
            }
            contentDescription = labelText
        }
        val labelView = TextView(context).apply {
            text = labelText
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            maxLines = 1
            setShadowLayer(4f, 0f, 1f, Color.BLACK)
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(iconView, LinearLayout.LayoutParams(buttonSizePx, buttonSizePx))
            addView(
                labelView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = (2 * density).toInt() },
            )
            setOnClickListener {
                item.onClick()
                onDismiss()
            }
        }
        addView(
            container,
            FrameLayout.LayoutParams(containerWidth, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = (x - containerWidth / 2f).toInt()
                topMargin = (y - buttonSizePx / 2f).toInt()
            },
        )

        // Aparece "saliendo" desde el centro de Nao.
        container.alpha = 0f
        container.scaleX = 0.3f
        container.scaleY = 0.3f
        container.translationX = centerX - x
        container.translationY = centerY - y
        container.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .translationX(0f).translationY(0f)
            .setDuration(ANIMATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private companion object {
        /** Ponlo en false cuando uses tus propios íconos dibujados, para que conserven sus colores. */
        const val TINT_ICONS = true

        const val DIM_COLOR = 0x66000000
        const val ANIMATION_MS = 180L
        val BUTTON_COLOR = 0xF01B1820.toInt()
        val STROKE_COLOR = 0xFFFF8FB8.toInt()
        val ICON_TINT = 0xFFFF8FB8.toInt()
    }
}
