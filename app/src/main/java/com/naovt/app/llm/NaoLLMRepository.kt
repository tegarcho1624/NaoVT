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
 * - Todo lo pesado (copiar el modelo, cargarlo, generar) corre en un solo hilo aparte, de uno en uno.
 * - Los avisos (onToken, onDone, onError y el resultado de la carga) llegan siempre al hilo principal,
 *   así que se pueden usar directo desde la interfaz.
 */
class NaoLLMRepository(context: Context) {

    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "nao-llm").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var loaded = false

    val isLoaded: Boolean get() = loaded

    /** Busca el modelo (en assets o importado) y lo carga en memoria. Si ya está cargado, responde Ready. */
    fun loadModel(onResult: (LlmLoadResult) -> Unit) {
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

    private companion object {
        const val TAG = "NaoLLMRepository"

        /** Personalidad de Nao. Corta a propósito: va más rápido y se lee mejor en voz alta. */
        const val SYSTEM_PROMPT =
            "Eres Nao, una mascota virtual cariñosa y juguetona que vive en el dispositivo de tu persona favorita. " +
                "Respondes siempre en español, con frases cortas y naturales (máximo 3 frases), " +
                "sin listas, sin emojis y sin formato especial. " +
                "Hablas con ternura y cercanía; si te cuentan algo difícil, primero consuelas. " +
                "No finjas ser una persona real. Responde solo como Nao, sin escribir lo que diría el usuario."
    }
}
