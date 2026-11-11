package com.naovt.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.naovt.app.R

/**
 * Panel de texto flotante junto a Nao: un campo para escribir y un botón de enviar.
 * La respuesta no se muestra: Nao la dice en voz alta. Mientras piensa se ven tres puntitos.
 *
 * Vive en su propia ventana, que SÍ puede tener foco (sin FLAG_NOT_FOCUSABLE), porque sin foco
 * Android no abre el teclado en un overlay.
 *
 * @param onSend recibe el texto; devuelve true si se aceptó (entonces el campo se vacía).
 * @param onClose se llama al pulsar "Atrás".
 */
@SuppressLint("ViewConstructor")
class MiniChatPanelView(
    context: Context,
    private val onSend: (String) -> Boolean,
    private val onClose: () -> Unit,
) : LinearLayout(context) {

    private val density = resources.displayMetrics.density
    private val handler = Handler(Looper.getMainLooper())
    private val inputMethodManager =
        context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    private val input: EditText
    private val sendButton: ImageButton
    private val dotsView: TextView

    private var thinking = false
    private var dotCount = 0
    private var wantsKeyboard = false

    private val dotsTick = object : Runnable {
        override fun run() {
            dotCount = dotCount % 3 + 1
            dotsView.text = ".".repeat(dotCount)
            handler.postDelayed(this, DOTS_MS)
        }
    }

    init {
        orientation = VERTICAL
        setPadding(dp(12), dp(8), dp(8), dp(8))
        background = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            setColor(PANEL_COLOR)
            setStroke(dp(2), ACCENT_COLOR)
        }

        input = EditText(context).apply {
            hint = context.getString(R.string.mini_chat_hint)
            setTextColor(Color.WHITE)
            setHintTextColor(HINT_COLOR)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setBackgroundColor(Color.TRANSPARENT)
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_SEND
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    submit()
                    true
                } else {
                    false
                }
            }
        }
        sendButton = ImageButton(context).apply {
            setImageResource(R.drawable.ic_mini_send)
            setColorFilter(ACCENT_COLOR)
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = context.getString(R.string.mini_chat_send)
            setOnClickListener { submit() }
        }
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(input, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(sendButton, LayoutParams(dp(48), dp(48)))
        }
        dotsView = TextView(context).apply {
            setTextColor(ACCENT_COLOR)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            visibility = GONE
        }
        addView(row, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(dotsView, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    /** Pide el teclado: se abre en cuanto la ventana recibe el foco. */
    fun requestKeyboard() {
        wantsKeyboard = true
    }

    /** Muestra u oculta los tres puntitos de "Nao está pensando". */
    fun setThinking(value: Boolean) {
        if (thinking == value) return
        thinking = value
        sendButton.alpha = if (value) 0.4f else 1f
        handler.removeCallbacks(dotsTick)
        if (value) {
            dotCount = 1
            dotsView.text = "."
            dotsView.visibility = VISIBLE
            handler.postDelayed(dotsTick, DOTS_MS)
        } else {
            dotsView.visibility = GONE
        }
    }

    /** Esconde el teclado y detiene la animación antes de quitar la ventana. */
    fun prepareToClose() {
        handler.removeCallbacks(dotsTick)
        inputMethodManager.hideSoftInputFromWindow(windowToken, 0)
    }

    private fun submit() {
        val text = input.text.toString().trim()
        if (text.isEmpty() || thinking) return
        if (onSend(text)) input.text.clear()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus && wantsKeyboard) {
            wantsKeyboard = false
            input.requestFocus()
            inputMethodManager.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    // "Atrás" cierra el panel (la ventana tiene el foco, así que recibe la tecla).
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) onClose()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(dotsTick)
        super.onDetachedFromWindow()
    }

    private fun dp(value: Int): Int = (value * density).toInt()

    private companion object {
        const val DOTS_MS = 400L
        val PANEL_COLOR = 0xF21B1820.toInt()
        val ACCENT_COLOR = 0xFFFF8FB8.toInt()
        val HINT_COLOR = 0x99FFFFFF.toInt()
    }
}
