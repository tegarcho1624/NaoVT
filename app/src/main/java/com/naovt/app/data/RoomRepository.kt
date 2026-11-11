package com.naovt.app.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.naovt.app.R
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Habitaciones de Nao.
 *
 * - Las predeterminadas (Cuarto de Naomi y Sala de estar) están en el código y no se guardan ni se borran.
 * - Las personalizadas se guardan como JSON en SharedPreferences ("naovt_rooms", clave "rooms_json":
 *   {"i": id, "n": nombre, "r": ruta, "t": "IMAGEN" | "VIDEO"}) y su archivo se COPIA a
 *   filesDir/habitaciones/, así que borrar el original de la galería no las rompe.
 * - También recuerda cuál es la habitación activa (clave "active_id").
 *
 * Las funciones hacen I/O: [copyToInternal] puede tardar con videos grandes (usarla fuera del hilo principal).
 */
class RoomRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun predeterminadas(): List<Habitacion> = listOf(
        Habitacion(
            id = ID_CUARTO,
            nombre = appContext.getString(R.string.room_nao_room),
            rutaArchivo = "res:nao_room",
            esPredeterminada = true,
            tipo = TipoHabitacion.IMAGEN,
            recurso = R.drawable.nao_room,
        ),
        Habitacion(
            id = ID_SALA,
            nombre = appContext.getString(R.string.room_sala_de_estar),
            rutaArchivo = "res:sala_de_estar",
            esPredeterminada = true,
            tipo = TipoHabitacion.IMAGEN,
            recurso = R.drawable.sala_de_estar,
        ),
    )

    /** Las que creó el usuario. Se omiten las que perdieron su archivo (sin borrar nada del registro). */
    fun personalizadas(): List<Habitacion> = synchronized(LOCK) {
        readCustom().filter { File(it.rutaArchivo).exists() }
    }

    fun getAll(): List<Habitacion> = predeterminadas() + personalizadas()

    fun activeId(): String = prefs.getString(KEY_ACTIVE, ID_CUARTO) ?: ID_CUARTO

    fun setActive(id: String) {
        prefs.edit().putString(KEY_ACTIVE, id).apply()
    }

    /** Registra una habitación personalizada cuyo archivo ya está copiado en [rutaArchivo]. */
    fun add(nombre: String, tipo: TipoHabitacion, rutaArchivo: String): Habitacion = synchronized(LOCK) {
        val habitacion = Habitacion(UUID.randomUUID().toString(), nombre, rutaArchivo, false, tipo)
        writeCustom(readCustom() + habitacion)
        habitacion
    }

    /**
     * Elimina una personalizada y su archivo. Las predeterminadas no se pueden eliminar (devuelve false).
     * Si era la activa, la activa pasa a ser la Sala de estar para que el fondo nunca quede en negro.
     */
    fun delete(id: String): Boolean = synchronized(LOCK) {
        val target = readCustom().firstOrNull { it.id == id } ?: return false
        writeCustom(readCustom().filterNot { it.id == id })
        val file = File(target.rutaArchivo)
        if (file.exists() && !file.delete()) Log.w(TAG, "No se pudo borrar ${file.name}")
        if (activeId() == id) setActive(ID_SALA)
        true
    }

    /** Copia la imagen o el video elegido a filesDir/habitaciones/. Falla si no se puede leer o escribir. */
    fun copyToInternal(source: Uri, tipo: TipoHabitacion): Result<File> = runCatching {
        val dir = File(appContext.filesDir, ROOMS_DIR).apply { mkdirs() }
        val extension = extensionFor(appContext.contentResolver.getType(source), tipo)
        val destination = File(dir, "${UUID.randomUUID()}.$extension")
        try {
            val input = appContext.contentResolver.openInputStream(source)
                ?: throw IOException("No se pudo abrir el archivo elegido")
            input.use { stream -> destination.outputStream().use { out -> stream.copyTo(out) } }
            if (destination.length() == 0L) throw IOException("El archivo copiado está vacío")
        } catch (e: Exception) {
            destination.delete() // sin archivos a medias
            throw e
        }
        destination
    }

    private fun extensionFor(mimeType: String?, tipo: TipoHabitacion): String = when (mimeType) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "image/jpeg" -> "jpg"
        "video/webm" -> "webm"
        "video/3gpp" -> "3gp"
        "video/mp4" -> "mp4"
        else -> if (tipo == TipoHabitacion.VIDEO) "mp4" else "jpg"
    }

    private fun readCustom(): List<Habitacion> {
        val raw = prefs.getString(KEY_LIST, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val json = array.getJSONObject(index)
                Habitacion(
                    id = json.getString(KEY_ID),
                    nombre = json.optString(KEY_NAME, ""),
                    rutaArchivo = json.getString(KEY_PATH),
                    esPredeterminada = false,
                    tipo = runCatching { TipoHabitacion.valueOf(json.optString(KEY_TYPE, "")) }
                        .getOrDefault(TipoHabitacion.IMAGEN),
                )
            }
        } catch (e: JSONException) {
            Log.e(TAG, "No se pudo leer la lista de habitaciones", e)
            emptyList()
        }
    }

    private fun writeCustom(rooms: List<Habitacion>) {
        val array = JSONArray()
        rooms.forEach { room ->
            array.put(
                JSONObject()
                    .put(KEY_ID, room.id)
                    .put(KEY_NAME, room.nombre)
                    .put(KEY_PATH, room.rutaArchivo)
                    .put(KEY_TYPE, room.tipo.name)
            )
        }
        prefs.edit().putString(KEY_LIST, array.toString()).apply()
    }

    companion object {
        const val ID_CUARTO = "nao_room"
        const val ID_SALA = "sala_de_estar"

        private const val TAG = "RoomRepository"
        private const val PREFS_NAME = "naovt_rooms"
        private const val KEY_LIST = "rooms_json"
        private const val KEY_ACTIVE = "active_id"
        private const val ROOMS_DIR = "habitaciones"
        private const val KEY_ID = "i"
        private const val KEY_NAME = "n"
        private const val KEY_PATH = "r"
        private const val KEY_TYPE = "t"
        private val LOCK = Any()
    }
}
