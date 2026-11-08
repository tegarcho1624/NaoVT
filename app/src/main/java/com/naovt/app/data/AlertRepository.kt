package com.naovt.app.data

import android.content.Context
import android.util.Log
import com.naovt.app.ui.avatar.NaoExpression
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Guarda las alertas como JSON en SharedPreferences (igual que [MemoryRepository]).
 *
 * No guarda copia en memoria: lee y escribe cada vez, porque la usan a la vez la pantalla,
 * el receptor de la alarma y el receptor de reinicio. Las funciones son rápidas, pero hacen I/O.
 */
class AlertRepository(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Todas las alertas, de la más próxima a la más lejana. */
    fun getAll(): List<Alert> = synchronized(LOCK) { read().sortedBy { it.triggerAtMillis } }

    fun get(id: Int): Alert? = synchronized(LOCK) { read().firstOrNull { it.id == id } }

    fun add(
        text: String,
        triggerAtMillis: Long,
        expression: NaoExpression,
        repeat: AlertRepeat = AlertRepeat.None,
    ): Alert =
        synchronized(LOCK) {
            val id = prefs.getInt(KEY_NEXT_ID, 1)
            val alert = Alert(id, text, triggerAtMillis, expression, repeat = repeat)
            write(read() + alert)
            prefs.edit().putInt(KEY_NEXT_ID, id + 1).apply()
            alert
        }

    fun markFired(id: Int) = synchronized(LOCK) {
        write(read().map { if (it.id == id) it.copy(fired = true) else it })
    }

    /** Mueve una alerta que se repite a su próxima vez. */
    fun updateTrigger(id: Int, newTriggerAtMillis: Long) = synchronized(LOCK) {
        write(read().map { if (it.id == id) it.copy(triggerAtMillis = newTriggerAtMillis) else it })
    }

    fun delete(id: Int) = synchronized(LOCK) {
        write(read().filterNot { it.id == id })
    }

    private fun write(alerts: List<Alert>) {
        val array = JSONArray()
        alerts.forEach { alert ->
            array.put(
                JSONObject()
                    .put(KEY_ID, alert.id)
                    .put(KEY_TEXT, alert.text)
                    .put(KEY_TIME, alert.triggerAtMillis)
                    .put(KEY_EXPRESSION, alert.expression.name)
                    .put(KEY_FIRED, alert.fired)
                    .put(KEY_REPEAT, alert.repeat.name)
            )
        }
        prefs.edit().putString(PREFS_KEY_LIST, array.toString()).apply()
    }

    private fun read(): List<Alert> {
        val raw = prefs.getString(PREFS_KEY_LIST, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val json = array.getJSONObject(index)
                Alert(
                    id = json.getInt(KEY_ID),
                    text = json.optString(KEY_TEXT, ""),
                    triggerAtMillis = json.optLong(KEY_TIME, 0L),
                    expression = runCatching {
                        NaoExpression.valueOf(json.optString(KEY_EXPRESSION, ""))
                    }.getOrDefault(NaoExpression.Neutral),
                    fired = json.optBoolean(KEY_FIRED, false),
                    // Las alertas guardadas antes de existir la repetición no la tienen: no se repiten.
                    repeat = runCatching {
                        AlertRepeat.valueOf(json.optString(KEY_REPEAT, ""))
                    }.getOrDefault(AlertRepeat.None),
                )
            }
        } catch (e: JSONException) {
            Log.e(TAG, "No se pudo leer la lista de alertas", e)
            emptyList()
        }
    }

    private companion object {
        const val TAG = "AlertRepository"
        const val PREFS_NAME = "naovt_alerts"
        const val PREFS_KEY_LIST = "alerts_json"
        const val KEY_NEXT_ID = "next_id"
        const val KEY_ID = "id"
        const val KEY_TEXT = "text"
        const val KEY_TIME = "time"
        const val KEY_EXPRESSION = "expression"
        const val KEY_FIRED = "fired"
        const val KEY_REPEAT = "repeat"
        val LOCK = Any()
    }
}
