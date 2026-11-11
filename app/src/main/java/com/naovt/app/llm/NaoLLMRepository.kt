package com.naovt.app.llm

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.llamatik.core.platform.LlamaBridge
import java.util.concurrent.Executors

/** Resultado de cargar el modelo. */
sealed interface LlmLoadResult {
    data object Ready : LlmLoadResult
    data object ModelNotFound : LlmLoadResult
    data class Failed(val message: String) : LlmLoadResult
}

/**
 * Cerebro local de Nao: carga un modelo .gguf con Llamatik (llama.cpp) y genera respuestas
 * por streaming, sin internet.
 *
 * Hay UNA sola instancia para toda la app ([get]), porque el modelo cargado en memoria también es
 * único: lo comparten el chat completo, el mini chat de voz de la pantalla principal y el de Nao flotante.
 *
 * Memoria: quien usa el modelo llama a [acquire] y, al terminar, a [release]. Cuando nadie lo está
 * usando, se descarga solo tras [IDLE_UNLOAD_MS] para no ocupar RAM de más. La siguiente vez que
 * se necesite se vuelve a cargar (tarda unos segundos).
 *
 * - Todo lo pesado (copiar el modelo, cargarlo, generar) corre en un solo hilo aparte, de uno en uno.
 * - Los avisos (onToken, onDone, onError y el resultado de la carga) llegan siempre al hilo principal.
 * - [acquire] y [release] se llaman desde el hilo principal.
 */
class NaoLLMRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "nao-llm").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var loaded = false

    private var holders = 0
    private val unloadWhenIdle = Runnable { if (holders == 0) unload() }

    val isLoaded: Boolean get() = loaded

    /** Marca que alguien está usando el modelo: no se descargará mientras tanto. */
    fun acquire() {
        holders++
        mainHandler.removeCallbacks(unloadWhenIdle)
    }

    /** Avisa que ya no se usa. Si nadie más lo usa, se descargará tras un rato. */
    fun release() {
        if (holders > 0) holders--
        if (holders == 0) {
            mainHandler.removeCallbacks(unloadWhenIdle)
            mainHandler.postDelayed(unloadWhenIdle, IDLE_UNLOAD_MS)
        }
    }

    /** Busca el modelo (en assets o importado) y lo carga en memoria. Si ya está cargado, responde Ready. */
    fun loadModel(onResult: (LlmLoadResult) -> Unit) {
        mainHandler.removeCallbacks(unloadWhenIdle)
        executor.execute {
            val result = if (loaded) LlmLoadResult.Ready else loadBlocking()
            mainHandler.post { onResult(result) }
        }
    }

    /**
     * Genera la respuesta de Nao a [prompt], token por token.
     * @param contexto resumen de la conversación reciente (opcional).
     */
    fun generarRespuesta(
        prompt: String,
        onToken: (String) -> Unit,
        onDone: () -> Unit,
        contexto: String = "",
        onError: (String) -> Unit = {},
    ) {
        executor.execute {
            if (!loaded) {
                mainHandler.post { onError("El modelo todavía no está cargado") }
                return@execute
            }
            try {
                LlamaBridge.generateWithContextStream(
                    system = SYSTEM_PROMPT,
                    context = contexto,
                    user = prompt,
                    onDelta = { token -> mainHandler.post { onToken(token) } },
                    onDone = { mainHandler.post { onDone() } },
                    onError = { message -> mainHandler.post { onError(message) } },
                )
            } catch (e: Throwable) {
                Log.e(TAG, "Falló la generación", e)
                mainHandler.post { onError(e.message ?: "Error al generar la respuesta") }
            }
        }
    }

    /** Corta la generación en curso (el modelo sigue cargado). */
    fun cancelar() {
        try {
            LlamaBridge.nativeCancelGenerate()
        } catch (e: Throwable) {
            Log.w(TAG, "No se pudo cancelar la generación", e)
        }
    }

    /** Descarga el modelo de la memoria (por ejemplo, antes de cargar uno nuevo). */
    fun unload() {
        executor.execute {
            if (loaded) {
                try {
                    LlamaBridge.shutdown()
                } catch (e: Throwable) {
                    Log.w(TAG, "Error al liberar el modelo", e)
                }
                loaded = false
            }
        }
    }

    private fun loadBlocking(): LlmLoadResult = try {
        val file = ModelLocator.prepare(appContext)
        if (file == null) {
            LlmLoadResult.ModelNotFound
        } else if (LlamaBridge.initGenerateModel(file.absolutePath)) {
            loaded = true
            LlmLoadResult.Ready
        } else {
            LlmLoadResult.Failed("No se pudo cargar ${file.name}. Puede estar dañado o ser demasiado grande para este dispositivo.")
        }
    } catch (e: OutOfMemoryError) {
        Log.e(TAG, "Sin memoria al cargar el modelo", e)
        LlmLoadResult.Failed("No hay suficiente memoria para este modelo. Prueba con uno más pequeño.")
    } catch (e: Throwable) {
        // Incluye errores al copiar el modelo (falta de espacio) y fallos del código nativo.
        Log.e(TAG, "Error al cargar el modelo", e)
        LlmLoadResult.Failed(e.message ?: "Error desconocido al cargar el modelo")
    }

    companion object {
        private const val TAG = "NaoLLMRepository"

        /** Tiempo sin uso tras el cual se libera la memoria del modelo. */
        private const val IDLE_UNLOAD_MS = 3 * 60_000L

        /** Personalidad de Nao. Corta a propósito: va más rápido y se lee mejor en voz alta. */
        private const val SYSTEM_PROMPT =
            "Eres Nao, una mascota virtual cariñosa y juguetona que vive en el dispositivo de tu persona favorita. " +
                "Respondes siempre en español, con frases cortas y naturales (máximo 3 frases), " +
                "sin listas, sin emojis y sin formato especial. " +
                "Hablas con ternura y cercanía; si te cuentan algo difícil, primero consuelas. " +
                "No finjas ser una persona real. Responde solo como Nao, sin escribir lo que diría el usuario."

        @Volatile
        private var instance: NaoLLMRepository? = null

        /** La única instancia de la app. */
        fun get(context: Context): NaoLLMRepository =
            instance ?: synchronized(this) {
                instance ?: NaoLLMRepository(context.applicationContext).also { instance = it }
            }
    }
}
