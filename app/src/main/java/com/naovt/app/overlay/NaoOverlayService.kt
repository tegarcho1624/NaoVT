package com.naovt.app.overlay

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlin.random.Random
import kotlin.math.min
import kotlin.math.roundToInt
import com.naovt.app.MainActivity
import com.naovt.app.R
import com.naovt.app.dialog.NaoPhrases
import com.naovt.app.voice.NaoVoice

/**
 * Servicio en primer plano que dibuja a Nao flotando sobre otras apps con WindowManager.
 *
 * Tamaño: se cambia pellizcando (entre [MIN_WIDTH_DP] y [MAX_WIDTH_DP]) y se recuerda.
 *
 * Cuidado de batería:
 * - START_NOT_STICKY: si el sistema lo cierra, no se reinicia solo.
 * - Sin trabajo en segundo plano mientras nadie la toca (no hay temporizadores activos en reposo).
 * - El motor de voz se crea al primer toque y se libera tras un rato sin usarse.
 * - Al detenerse quita la ventana, libera la voz y las imágenes.
 */
class NaoOverlayService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager

    private var overlay: NaoOverlayView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var overlaySize = Point(0, 0)
    private var widthPx = 0f
    private var minWidthPx = 0f
    private var maxWidthPx = 0f

    private var voice: NaoVoice? = null
    private var lastPhrase: String? = null
    private var talkWaitUntil = 0L

    // Cariño activo (no se parpadea mientras dura).
    private var lovedActive = false

    // Terminó el cariño: vuelve a la normalidad y entonces dice su frase.
    private val afterLove = Runnable {
        lovedActive = false
        overlay?.setLoved(false)
        if (OVERLAY_SPEAKS) speakPhrase()
    }
    private val releaseVoice = Runnable {
        voice?.shutdown()
        voice = null
    }

    // Anima la boca solo mientras Nao habla; luego se detiene sola.
    private var mouthToggle = false
    private val mouthTick = object : Runnable {
        override fun run() {
            val view = overlay ?: return
            if (voice?.isSpeaking == true) {
                mouthToggle = !mouthToggle
                view.setTalkingMouth(mouthToggle)
                handler.postDelayed(this, MOUTH_TOGGLE_MS)
            } else if (SystemClock.uptimeMillis() < talkWaitUntil) {
                handler.postDelayed(this, MOUTH_TOGGLE_MS) // la voz aún no arranca
            } else {
                view.setTalkingMouth(null)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Debe ir primero: Android exige mostrar la notificación enseguida.
        OverlayNotification.ensureChannel(this)
        ServiceCompat.startForeground(
            this,
            OverlayNotification.NOTIFICATION_ID,
            OverlayNotification.build(this),
            foregroundServiceType(),
        )

        if (overlay == null) showOverlay()
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Rotación: ajustar los límites de tamaño y mantener a Nao dentro de la pantalla.
        refreshSizeLimits()
        resizeBy(1f)
        moveTo(layoutParams?.x ?: 0, layoutParams?.y ?: 0)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        overlay?.let { view ->
            runCatching { windowManager.removeView(view) }
            view.release()
        }
        overlay = null
        layoutParams = null
        if (screenReceiverRegistered) runCatching { unregisterReceiver(screenReceiver) }
        screenReceiverRegistered = false
        releaseVoice.run()
        isRunning = false
        super.onDestroy()
    }

    // region Ventana flotante

    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            fail(R.string.overlay_permission_missing)
            return
        }

        // Tamaño guardado (o el predeterminado), dentro de los límites.
        refreshSizeLimits()
        val savedWidthDp = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_WIDTH_DP, DEFAULT_WIDTH_DP)
        widthPx = (savedWidthDp * resources.displayMetrics.density).coerceIn(minWidthPx, maxWidthPx)
        overlaySize = Point(widthPx.roundToInt(), (widthPx * LAYER_ASPECT).roundToInt())

        val view = try {
            NaoOverlayView(this, overlaySize.y)
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo crear la vista flotante", e)
            fail(R.string.overlay_failed)
            return
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Sin memoria para la vista flotante", e)
            fail(R.string.overlay_failed)
            return
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        // La ventana mide exactamente lo que Nao: los toques fuera de ella pasan a la app de abajo.
        // NOT_FOCUSABLE evita robar el teclado/foco; NOT_TOUCH_MODAL deja pasar los toques de afuera.
        val params = WindowManager.LayoutParams(
            overlaySize.x,
            overlaySize.y,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val saved = loadPosition()
            x = saved.x
            y = saved.y
        }

        view.onTap = ::onNaoTapped
        view.onOpenApp = ::openApp
        view.positionProvider = { Point(params.x, params.y) }
        view.onMove = ::moveTo
        view.onMoveEnd = ::savePlacement
        view.onScale = ::resizeBy
        view.onScaleEnd = ::onResizeEnd

        try {
            windowManager.addView(view, params)
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo mostrar la ventana flotante", e)
            view.release()
            fail(R.string.overlay_failed)
            return
        }

        overlay = view
        layoutParams = params
        moveTo(params.x, params.y) // por si la posición guardada quedó fuera de pantalla
        startBlinking()
    }

    private fun moveTo(x: Int, y: Int) {
        val view = overlay ?: return
        val params = layoutParams ?: return
        val screen = screenSize()
        params.x = x.coerceIn(0, (screen.x - overlaySize.x).coerceAtLeast(0))
        params.y = y.coerceIn(0, (screen.y - overlaySize.y).coerceAtLeast(0))
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    /** Pellizco: cambia el ancho de Nao por [factor], manteniendo su centro en el mismo lugar. */
    private fun resizeBy(factor: Float) {
        val params = layoutParams ?: return
        val newWidth = (widthPx * factor).coerceIn(minWidthPx, maxWidthPx)
        if (newWidth == widthPx) return

        val centerX = params.x + overlaySize.x / 2f
        val centerY = params.y + overlaySize.y / 2f

        widthPx = newWidth
        overlaySize = Point(newWidth.roundToInt(), (newWidth * LAYER_ASPECT).roundToInt())
        params.width = overlaySize.x
        params.height = overlaySize.y
        // moveTo aplica el nuevo tamaño y la deja dentro de la pantalla.
        moveTo(
            (centerX - overlaySize.x / 2f).roundToInt(),
            (centerY - overlaySize.y / 2f).roundToInt(),
        )
    }

    /** Al soltar el pellizco: recuerda el tamaño y ajusta la calidad de las imágenes. */
    private fun onResizeEnd() {
        overlay?.updateResolution(overlaySize.y)
        savePlacement()
    }

    /** Límites del ancho: entre MIN y MAX dp, y nunca más grande que la pantalla. */
    private fun refreshSizeLimits() {
        val density = resources.displayMetrics.density
        val screen = screenSize()
        minWidthPx = MIN_WIDTH_DP * density
        maxWidthPx = maxOf(
            minWidthPx,
            min(MAX_WIDTH_DP * density, min(screen.x.toFloat(), screen.y / LAYER_ASPECT)),
        )
    }

    private fun screenSize(): Point =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            Point(bounds.width(), bounds.height())
        } else {
            Point().also {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.getSize(it)
            }
        }

    // endregion

    // region Parpadeo

    private var screenReceiverRegistered = false

    // Con la pantalla apagada se detiene el parpadeo; al encenderla se reanuda.
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> stopBlinking()
                Intent.ACTION_SCREEN_ON -> scheduleBlink()
            }
        }
    }

    private val blinkTick = Runnable {
        val view = overlay ?: return@Runnable
        // Solo parpadea en estado base: sin cariño y sin hablar.
        if (!lovedActive && voice?.isSpeaking != true) {
            view.setBlinking(true)
            handler.postDelayed(openEyes, BLINK_DURATION_MS)
        }
        scheduleBlink()
    }
    private val openEyes = Runnable { overlay?.setBlinking(false) }

    private fun startBlinking() {
        if (!OVERLAY_BLINKS) return
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        screenReceiverRegistered = true
        if (getSystemService(PowerManager::class.java).isInteractive) scheduleBlink()
    }

    private fun scheduleBlink() {
        handler.removeCallbacks(blinkTick)
        handler.postDelayed(blinkTick, Random.nextLong(BLINK_MIN_WAIT_MS, BLINK_MAX_WAIT_MS))
    }

    private fun stopBlinking() {
        handler.removeCallbacks(blinkTick)
        handler.removeCallbacks(openEyes)
        overlay?.setBlinking(false)
    }

    // endregion

    // region Posición recordada

    private fun loadPosition(): Point {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.contains(KEY_X) && prefs.contains(KEY_Y)) {
            return Point(prefs.getInt(KEY_X, 0), prefs.getInt(KEY_Y, 0))
        }
        // Primera vez: esquina inferior derecha.
        val screen = screenSize()
        return Point(
            screen.x - overlaySize.x - dp(16),
            screen.y - overlaySize.y - dp(96),
        )
    }

    /** Guarda posición y tamaño para la próxima vez que se active el Modo Flotante. */
    private fun savePlacement() {
        val params = layoutParams ?: return
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt(KEY_X, params.x)
            .putInt(KEY_Y, params.y)
            .putFloat(KEY_WIDTH_DP, widthPx / resources.displayMetrics.density)
            .apply()
    }

    // endregion

    // region Interacción

    private fun onNaoTapped() {
        val view = overlay ?: return

        // Si ya está hablando, corta y sigue con otra frase (hablar y cariño no se mezclan).
        if (OVERLAY_SPEAKS && voice?.isSpeaking == true) {
            handler.removeCallbacks(afterLove)
            speakPhrase()
            return
        }

        // Cariño: cara de beso por 2 segundos (cada toque reinicia el tiempo) y luego habla.
        lovedActive = true
        view.setLoved(true)
        handler.removeCallbacks(afterLove)
        handler.postDelayed(afterLove, LOVED_DURATION_MS)
    }

    private fun speakPhrase() {
        val engine = voice ?: NaoVoice(this).also { voice = it }
        val phrase = NaoPhrases.random(previous = lastPhrase)
        lastPhrase = phrase
        engine.speak(phrase)

        talkWaitUntil = SystemClock.uptimeMillis() + VOICE_START_WAIT_MS
        handler.removeCallbacks(mouthTick)
        handler.post(mouthTick)

        // El motor de voz se libera si Nao lleva un rato sin que la toquen.
        handler.removeCallbacks(releaseVoice)
        handler.postDelayed(releaseVoice, VOICE_IDLE_RELEASE_MS)
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "El sistema no permitió abrir la app desde el overlay", e)
            Toast.makeText(this, R.string.overlay_open_blocked, Toast.LENGTH_LONG).show()
        }
    }

    // endregion

    private fun fail(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_LONG).show()
        stopSelf()
    }

    private fun foregroundServiceType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val ACTION_STOP = "com.naovt.app.action.STOP_OVERLAY"

        private const val TAG = "NaoOverlayService"
        private const val PREFS_NAME = "naovt_overlay"
        private const val KEY_X = "x"
        private const val KEY_Y = "y"

        private const val KEY_WIDTH_DP = "width_dp"

        /** Ancho inicial y límites del pellizco (en dp). Las capas miden 1024x1536. */
        private const val DEFAULT_WIDTH_DP = 133f
        private const val MIN_WIDTH_DP = 100f
        private const val MAX_WIDTH_DP = 300f
        private const val LAYER_ASPECT = 1536f / 1024f

        /** false = Nao flotante muda (solo cara de beso). */
        private const val OVERLAY_SPEAKS = true

        /** false = Nao flotante no parpadea. */
        private const val OVERLAY_BLINKS = true
        private const val BLINK_MIN_WAIT_MS = 3000L
        private const val BLINK_MAX_WAIT_MS = 6000L
        private const val BLINK_DURATION_MS = 150L

        private const val LOVED_DURATION_MS = 2000L
        private const val MOUTH_TOGGLE_MS = 140L
        private const val VOICE_START_WAIT_MS = 1500L
        private const val VOICE_IDLE_RELEASE_MS = 60_000L

        /** Observable desde Compose para cambiar el texto del botón. */
        var isRunning by mutableStateOf(false)
            private set
    }
}
