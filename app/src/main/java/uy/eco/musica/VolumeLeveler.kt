package uy.eco.musica

import android.content.Context
import android.media.audiofx.DynamicsProcessing
import android.os.Build

/**
 * «Volumen parejo»: emparejar el volumen entre canciones (los temas de YouTube vienen con
 * volúmenes muy distintos). Usa el procesador de audio de Android: un compresor suave que
 * baja los temas muy fuertes y sube los bajitos, más un limitador para que nada sature.
 * No agrega peso a la app. Requiere Android 9 o más nuevo; en los anteriores no hace nada.
 */
class VolumeLeveler {
    private var effect: DynamicsProcessing? = null
    private var session = 0
    private var enabled = true
    private var reported = false

    /** Se llama cuando el reproductor tiene (o cambia) su sesión de audio. */
    fun attach(audioSessionId: Int) {
        if (audioSessionId == 0 || audioSessionId == session) return
        release()
        session = audioSessionId
        apply()
    }

    fun setEnabled(on: Boolean) {
        enabled = on
        apply()
    }

    private fun apply() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || session == 0) return
        try {
            if (!enabled) {
                effect?.enabled = false
                return
            }
            val fx = effect ?: create(session).also { effect = it }
            fx.enabled = true
        } catch (e: Exception) {
            // Algunos teléfonos no lo soportan: se sigue sin emparejar, sin molestar.
            effect = null
            if (!reported) {
                reported = true
                ErrorLog.report("Volumen parejo", ErrorLog.describe(e))
            }
        }
    }

    private fun create(sessionId: Int): DynamicsProcessing {
        val config = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            2,            // canales
            false, 0,     // ecualizador previo: no
            true, 1,      // compresor: una sola banda (todo el sonido)
            false, 0,     // ecualizador posterior: no
            true,         // limitador: sí
        ).build()
        config.setMbcBandAllChannelsTo(
            0,
            DynamicsProcessing.MbcBand(
                true, true,
                20_000f,  // toda la banda de frecuencias
                20f,      // ataque (ms)
                400f,     // relajación (ms)
                3f,       // relación de compresión
                -26f,     // umbral (dB)
                8f,       // suavidad de la curva (dB)
                -90f,     // puerta de ruido: apagada
                1f,       // expansor: apagado
                0f,       // ganancia antes (dB)
                8f,       // ganancia después (dB): recupera el volumen general
            )
        )
        config.setLimiterAllChannelsTo(
            DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 10f, -2f, 0f)
        )
        return DynamicsProcessing(0, sessionId, config)
    }

    fun release() {
        runCatching { effect?.release() }
        effect = null
        session = 0
    }

    companion object {
        private const val PREFS = "alfa"
        const val KEY = "volumen_parejo"

        fun isOn(context: Context): Boolean =
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)

        fun set(context: Context, on: Boolean) {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY, on).apply()
        }

        val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    }
}
