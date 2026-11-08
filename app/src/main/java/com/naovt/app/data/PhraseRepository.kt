package com.naovt.app.data

import android.content.Context
import android.util.Log
import com.naovt.app.dialog.NaoMemoryPhrases
import com.naovt.app.dialog.NaoPhrases
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Guarda las frases de Nao como JSON en SharedPreferences (igual que [MemoryRepository]).
 *
 * La primera vez (todavía no existe la lista) se carga con las frases que ya venían en el código,
 * todas como "Normal". Después la lista es del usuario: si borra todas, no se vuelven a cargar.
 *
 * No guarda copia en memoria porque la usan la pantalla principal, el Modo Flotante y la pantalla
 * de frases a la vez. Hace I/O pequeño: es rápido, pero evita llamarlo en bucles.
 */
class PhraseRepository(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAll(): List<Phrase> = synchronized(LOCK) { load() }

    fun add(text: String, category: PhraseCategory): Phrase = synchronized(LOCK) {
        val phrases = load()
        val nextId = maxOf(prefs.getInt(KEY_NEXT_ID, 1), (phrases.maxOfOrNull { it.id } ?: 0) + 1)
        val phrase = Phrase(nextId, text, category)
        write(phrases + phrase, nextId + 1)
        phrase
    }

    fun update(id: Int, text: String, category: PhraseCategory) = synchronized(LOCK) {
        val phrases = load().map { if (it.id == id) it.copy(text = text, category = category) else it }
        write(phrases, prefs.getInt(KEY_NEXT_ID, 1))
    }

    fun delete(id: Int) = synchronized(LOCK) {
        write(load().filterNot { it.id == id }, prefs.getInt(KEY_NEXT_ID, 1))
    }

    /**
     * Frase al azar para cuando alguien toca a Nao. Elige solo entre las "Normal" para que las de
     * "Consuelo" o "Meme" no salgan solas (esas se escuchan tocándolas en la lista). Si no hay
     * ninguna "Normal" usa cualquiera, y si la lista está vacía devuelve null (Nao no dice nada).
     * Evita repetir la [previous].
     */
    fun random(previous: String? = null): String? {
        val all = getAll()
        val pool = all.filter { it.category == PhraseCategory.Normal }.ifEmpty { all }
        val candidates = if (pool.size > 1) pool.filter { it.text != previous } else pool
        return candidates.randomOrNull()?.text
    }

    private fun load(): List<Phrase> {
        val raw = prefs.getString(KEY_LIST, null)
        if (raw == null) {
            // Primera vez: se cargan las frases que ya existían en el código.
            val seed = (NaoPhrases.defaults + NaoMemoryPhrases.defaults)
                .distinct()
                .mapIndexed { index, text -> Phrase(index + 1, text, PhraseCategory.Normal) }
            write(seed, seed.size + 1)
            return seed
        }
        return try {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val json = array.getJSONObject(index)
                Phrase(
                    id = json.getInt(KEY_ID),
                    text = json.optString(KEY_TEXT, ""),
                    category = runCatching {
                        PhraseCategory.valueOf(json.optString(KEY_CATEGORY, ""))
                    }.getOrDefault(PhraseCategory.Normal),
                )
            }
        } catch (e: JSONException) {
            Log.e(TAG, "No se pudo leer la lista de frases", e)
            emptyList()
        }
    }

    private fun write(phrases: List<Phrase>, nextId: Int) {
        val array = JSONArray()
        phrases.forEach { phrase ->
            array.put(
                JSONObject()
                    .put(KEY_ID, phrase.id)
                    .put(KEY_TEXT, phrase.text)
                    .put(KEY_CATEGORY, phrase.category.name)
            )
        }
        prefs.edit()
            .putString(KEY_LIST, array.toString())
            .putInt(KEY_NEXT_ID, nextId)
            .apply()
    }

    private companion object {
        const val TAG = "PhraseRepository"
        const val PREFS_NAME = "naovt_phrases"
        const val KEY_LIST = "phrases_json"
        const val KEY_NEXT_ID = "next_id"
        const val KEY_ID = "id"
        const val KEY_TEXT = "text"
        const val KEY_CATEGORY = "category"
        val LOCK = Any()
    }
}
