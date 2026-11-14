package com.naovt.app.gestures

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import org.json.JSONObject

/**
 * Cómo se anima Nao en cada gesto. Los ojos y la boca son identificadores de asset (los mismos que
 * usa el Armario: nombre de archivo, o `custom/…` para los importados). `null` = "no cambiar":
 * se queda el ojo o la boca que Nao lleva puesta en el Armario.
 *
 * Los valores por defecto son los de siempre:
 * - Pestañeo: ojos cerrados felices; cada 3 a 6 s (4.5 s de promedio) durante 0.15 s.
 * - Hablar: la boca alterna con la boca abierta feliz.
 * - Beso: ojos con corazón y boca de beso.
 */
data class GestureSettings(
    val blinkEyes: String? = "ojos_feliz_cerrando.png",
    val blinkMouth: String? = null,
    /** Cada cuántos segundos parpadea, en promedio (el momento exacto varía un poco al azar). */
    val blinkIntervalSec: Float = 4.5f,
    /** Cuántos segundos se quedan cerrados los ojos. */
    val blinkDurationSec: Float = 0.15f,
    val talkEyes: String? = null,
    val talkMouth: String? = "boca_abierta_feliz.png",
    val kissEyes: String? = "ojos_corazon_mejilla.png",
    val kissMouth: String? = "boca_besando.png",
) {
    /** JSON para guardarlo dentro de un outfit. "" = "no cambiar" (null) a propósito. */
    fun toJson(): JSONObject = JSONObject()
        .put("blinkEyes", blinkEyes.orEmpty())
        .put("blinkMouth", blinkMouth.orEmpty())
        .put("blinkIntervalSec", blinkIntervalSec.toDouble())
        .put("blinkDurationSec", blinkDurationSec.toDouble())
        .put("talkEyes", talkEyes.orEmpty())
        .put("talkMouth", talkMouth.orEmpty())
        .put("kissEyes", kissEyes.orEmpty())
        .put("kissMouth", kissMouth.orEmpty())

    /** Copia con los valores de siempre en el grupo [group] (el resto no cambia). */
    fun withDefaults(group: GestureGroup): GestureSettings {
        val d = GestureSettings()
        return when (group) {
            GestureGroup.Blink -> copy(
                blinkEyes = d.blinkEyes,
                blinkMouth = d.blinkMouth,
                blinkIntervalSec = d.blinkIntervalSec,
                blinkDurationSec = d.blinkDurationSec,
            )
            GestureGroup.Talk -> copy(talkEyes = d.talkEyes, talkMouth = d.talkMouth)
            GestureGroup.Kiss -> copy(kissEyes = d.kissEyes, kissMouth = d.kissMouth)
        }
    }

    /** Copia con los tiempos dentro de los rangos permitidos. */
    fun clamped() = copy(
        blinkIntervalSec = blinkIntervalSec.coerceIn(INTERVAL_RANGE),
        blinkDurationSec = blinkDurationSec.coerceIn(DURATION_RANGE),
    )

    companion object {
        val INTERVAL_RANGE = 2f..10f
        val DURATION_RANGE = 0.1f..0.5f

        /** Lee lo guardado por [toJson]; lo que falte toma el valor de siempre. */
        fun fromJson(json: JSONObject): GestureSettings {
            val d = GestureSettings()
            fun asset(key: String, default: String?): String? =
                if (json.has(key)) json.optString(key, "").ifEmpty { null } else default
            return GestureSettings(
                blinkEyes = asset("blinkEyes", d.blinkEyes),
                blinkMouth = asset("blinkMouth", d.blinkMouth),
                blinkIntervalSec = json.optDouble("blinkIntervalSec", d.blinkIntervalSec.toDouble()).toFloat(),
                blinkDurationSec = json.optDouble("blinkDurationSec", d.blinkDurationSec.toDouble()).toFloat(),
                talkEyes = asset("talkEyes", d.talkEyes),
                talkMouth = asset("talkMouth", d.talkMouth),
                kissEyes = asset("kissEyes", d.kissEyes),
                kissMouth = asset("kissMouth", d.kissMouth),
            ).clamped()
        }
    }
}

/**
 * Los gestos de un personaje: los globales del Menú de Gestos, o los propios de un outfit
 * ([com.naovt.app.wardrobe.Outfit.gestures]). Es el mismo modelo en ambos casos.
 */
typealias CharacterGestures = GestureSettings

/** Grupos de ajustes que se pueden restablecer por separado. */
enum class GestureGroup { Blink, Talk, Kiss }

/**
 * Guarda los ajustes de gestos en SharedPreferences y los comparte con toda la app (misma Activity):
 * cambiarlos aquí se nota en el siguiente parpadeo, frase o beso, sin reiniciar nada.
 */
class GesturesViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var settings by mutableStateOf(load())
        private set

    fun update(transform: (GestureSettings) -> GestureSettings) {
        settings = transform(settings).clamped()
        save(settings)
    }

    /** Vuelve a los valores de siempre del grupo [group]. */
    fun reset(group: GestureGroup) {
        update { it.withDefaults(group) }
    }

    private fun load(): GestureSettings {
        val d = GestureSettings()
        return GestureSettings(
            blinkEyes = readAsset(K_BLINK_EYES, d.blinkEyes),
            blinkMouth = readAsset(K_BLINK_MOUTH, d.blinkMouth),
            blinkIntervalSec = prefs.getFloat(K_BLINK_INTERVAL, d.blinkIntervalSec),
            blinkDurationSec = prefs.getFloat(K_BLINK_DURATION, d.blinkDurationSec),
            talkEyes = readAsset(K_TALK_EYES, d.talkEyes),
            talkMouth = readAsset(K_TALK_MOUTH, d.talkMouth),
            kissEyes = readAsset(K_KISS_EYES, d.kissEyes),
            kissMouth = readAsset(K_KISS_MOUTH, d.kissMouth),
        ).clamped()
    }

    /** Sin valor guardado = el de siempre; "" guardado = "no cambiar" (null) a propósito. */
    private fun readAsset(key: String, default: String?): String? =
        if (!prefs.contains(key)) default else prefs.getString(key, "")?.ifEmpty { null }

    private fun save(s: GestureSettings) {
        prefs.edit()
            .putString(K_BLINK_EYES, s.blinkEyes.orEmpty())
            .putString(K_BLINK_MOUTH, s.blinkMouth.orEmpty())
            .putFloat(K_BLINK_INTERVAL, s.blinkIntervalSec)
            .putFloat(K_BLINK_DURATION, s.blinkDurationSec)
            .putString(K_TALK_EYES, s.talkEyes.orEmpty())
            .putString(K_TALK_MOUTH, s.talkMouth.orEmpty())
            .putString(K_KISS_EYES, s.kissEyes.orEmpty())
            .putString(K_KISS_MOUTH, s.kissMouth.orEmpty())
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "naovt_gestures"
        const val K_BLINK_EYES = "blink_eyes"
        const val K_BLINK_MOUTH = "blink_mouth"
        const val K_BLINK_INTERVAL = "blink_interval_sec"
        const val K_BLINK_DURATION = "blink_duration_sec"
        const val K_TALK_EYES = "talk_eyes"
        const val K_TALK_MOUTH = "talk_mouth"
        const val K_KISS_EYES = "kiss_eyes"
        const val K_KISS_MOUTH = "kiss_mouth"
    }
}
