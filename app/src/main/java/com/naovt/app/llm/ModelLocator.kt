package com.naovt.app.llm

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException

/** El archivo elegido no es un modelo GGUF. */
class NotGgufException : IOException("El archivo no parece un modelo .gguf")

/**
 * Busca el modelo de Nao. Los modelos viven en la memoria interna de la app (filesDir/models/):
 * - los que se ponen en assets/models/ se copian ahí la primera vez;
 * - los que el usuario importa desde el teléfono se copian ahí directamente.
 * Si hay varios, se usa el más reciente.
 */
object ModelLocator {

    private const val ASSETS_DIR = "models"
    private const val FILES_DIR = "models"
    private const val EXTENSION = ".gguf"
    private val GGUF_MAGIC = "GGUF".toByteArray(Charsets.US_ASCII)

    fun modelsDir(context: Context): File =
        File(context.filesDir, FILES_DIR).apply { mkdirs() }

    /** Devuelve el modelo a usar, o null si no hay ninguno. Puede tardar si copia desde assets. */
    fun prepare(context: Context): File? {
        copyFromAssets(context)
        return modelsDir(context)
            .listFiles { file -> file.isFile && file.name.endsWith(EXTENSION, ignoreCase = true) }
            ?.maxByOrNull { it.lastModified() }
    }

    /** Copia un modelo elegido por el usuario. Falla con [NotGgufException] si no es un GGUF. */
    fun import(context: Context, uri: Uri): Result<File> = runCatching {
        val resolver = context.contentResolver
        val name = safeName(displayName(resolver, uri))
        val dir = modelsDir(context)
        val destination = File(dir, name)
        val temp = File(dir, "$name.part")
        try {
            val input = resolver.openInputStream(uri) ?: throw IOException("No se pudo abrir el archivo")
            input.use { stream ->
                val head = ByteArray(GGUF_MAGIC.size)
                val read = stream.read(head)
                if (read != head.size || !head.contentEquals(GGUF_MAGIC)) throw NotGgufException()
                temp.outputStream().use { out ->
                    out.write(head)
                    stream.copyTo(out)
                }
            }
            if (!temp.renameTo(destination)) {
                temp.copyTo(destination, overwrite = true)
                temp.delete()
            }
            destination.setLastModified(System.currentTimeMillis())
        } catch (e: Exception) {
            temp.delete()
            throw e
        }
        destination
    }

    private fun copyFromAssets(context: Context) {
        val names = try {
            context.assets.list(ASSETS_DIR)?.filter { it.endsWith(EXTENSION, ignoreCase = true) }
        } catch (e: IOException) {
            null
        } ?: return

        val dir = modelsDir(context)
        for (name in names) {
            val destination = File(dir, name)
            // Con el archivo sin comprimir se puede saber su tamaño y detectar copias incompletas.
            val assetLength = runCatching {
                context.assets.openFd("$ASSETS_DIR/$name").use { it.length }
            }.getOrNull()
            if (destination.exists() && (assetLength == null || destination.length() == assetLength)) continue

            val temp = File(dir, "$name.part")
            try {
                context.assets.open("$ASSETS_DIR/$name").use { input ->
                    temp.outputStream().use { out -> input.copyTo(out) }
                }
                if (!temp.renameTo(destination)) {
                    temp.copyTo(destination, overwrite = true)
                    temp.delete()
                }
            } catch (e: IOException) {
                temp.delete()
                throw e
            }
        }
    }

    private fun displayName(resolver: ContentResolver, uri: Uri): String? =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    private fun safeName(name: String?): String {
        val clean = name?.replace(Regex("[^A-Za-z0-9._-]"), "_").orEmpty()
        return if (clean.endsWith(EXTENSION, ignoreCase = true)) clean else "modelo_importado$EXTENSION"
    }
}
