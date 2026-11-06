package com.naovt.app.data

import android.content.Context
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Guarda los recuerdos: las fotos se copian a filesDir/recuerdos/ y la lista
 * (ruta, nota, fecha) se guarda como JSON en SharedPreferences.
 *
 * Es la única clase que sabe cómo se guarda todo; si algún día se cambia a Room,
 * solo hay que reemplazar esta clase. Las funciones hacen I/O: llamarlas fuera del hilo principal.
 */
class MemoryRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Más nuevos primero.
    private val items: MutableList<Memory> by lazy { readFromPrefs() }

    /** Todos los recuerdos, del más nuevo al más viejo (omite los que perdieron su archivo). */
    @Synchronized
    fun getAll(): List<Memory> = items.filter { File(it.imagePath).exists() }

    /**
     * Copia la imagen de [source] a la carpeta interna y registra el recuerdo.
     * Devuelve failure si la imagen no se pudo leer o copiar (y no deja archivos a medias).
     */
    @Synchronized
    fun add(source: Uri, note: String): Result<Memory> = runCatching {
        val dir = File(appContext.filesDir, MEMORIES_DIR)
        if (!dir.exists() && !dir.mkdirs()) throw IOException("No se pudo crear la carpeta de recuerdos")

        val id = UUID.randomUUID().toString()
        val extension = extensionFor(appContext.contentResolver.getType(source))
        val destination = File(dir, "$id.$extension")

        try {
            val input = appContext.contentResolver.openInputStream(source)
                ?: throw IOException("No se pudo abrir la imagen elegida")
            input.use { stream -> destination.outputStream().use { out -> stream.copyTo(out) } }
            if (destination.length() == 0L) throw IOException("La imagen copiada está vacía")
        } catch (e: Exception) {
            destination.delete()
            throw e
        }

        val memory = Memory(id, destination.absolutePath, note, System.currentTimeMillis())
        items.add(0, memory)
        save()
        memory
    }

    /** Quita el recuerdo de la lista y borra su foto copiada. */
    @Synchronized
    fun delete(memory: Memory) {
        items.removeAll { it.id == memory.id }
        save()
        val file = File(memory.imagePath)
        if (file.exists() && !file.delete()) {
            Log.w(TAG, "No se pudo borrar el archivo ${file.name}")
        }
    }

    private fun extensionFor(mimeType: String?): String = when (mimeType) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "image/heic" -> "heic"
        else -> "jpg"
    }

    private fun save() {
        val array = JSONArray()
        items.forEach { memory ->
            array.put(
                JSONObject()
                    .put(KEY_ID, memory.id)
                    .put(KEY_PATH, memory.imagePath)
                    .put(KEY_NOTE, memory.note)
                    .put(KEY_CREATED_AT, memory.createdAt)
            )
        }
        prefs.edit().putString(PREFS_KEY_LIST, array.toString()).apply()
    }

    private fun readFromPrefs(): MutableList<Memory> {
        val raw = prefs.getString(PREFS_KEY_LIST, null) ?: return mutableListOf()
        return try {
            val array = JSONArray(raw)
            MutableList(array.length()) { index ->
                val json = array.getJSONObject(index)
                Memory(
                    id = json.getString(KEY_ID),
                    imagePath = json.getString(KEY_PATH),
                    note = json.optString(KEY_NOTE, ""),
                    createdAt = json.optLong(KEY_CREATED_AT, 0L),
                )
            }
        } catch (e: JSONException) {
            Log.e(TAG, "No se pudo leer la lista de recuerdos", e)
            mutableListOf()
        }
    }

    private companion object {
        const val TAG = "MemoryRepository"
        const val PREFS_NAME = "naovt_memories"
        const val PREFS_KEY_LIST = "memories_json"
        const val MEMORIES_DIR = "recuerdos"
        const val KEY_ID = "id"
        const val KEY_PATH = "path"
        const val KEY_NOTE = "note"
        const val KEY_CREATED_AT = "createdAt"
    }
}
