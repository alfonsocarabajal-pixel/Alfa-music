package uy.eco.musica

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URLEncoder

/** Un error informado por algún teléfono (lo ve el administrador en Invitaciones). */
data class ErrorReport(
    val id: String,
    val who: String,
    val model: String,
    val version: Int,
    val time: Long,
    val kind: String,
    val detail: String,
)

/**
 * Registro de errores: cuando algo falla en un teléfono, se anota en Firebase quién, con qué
 * teléfono y versión, cuándo y qué pasó. Así se puede ver el problema sin pedir capturas.
 * Nunca frena la app: si no hay internet o falla, se descarta en silencio.
 * Límites: el mismo error se anota como mucho una vez cada 30 minutos y hasta 30 por día.
 */
object ErrorLog {

    private const val MAX_PER_DAY = 30
    private const val SAME_ERROR_GAP_MS = 30 * 60_000L
    private const val MAX_DETAIL = 900

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sp: SharedPreferences? = null
    private var model = ""

    /** Quién usa este teléfono (el nombre de su invitación). Lo fija [Access]. */
    @Volatile var who: String = ""

    private val path get() = "${Cloud.groupPath}/errores"

    fun init(context: Context) {
        sp = context.applicationContext.getSharedPreferences("errores", Context.MODE_PRIVATE)
        model = listOf(Build.MANUFACTURER, Build.MODEL)
            .filter { !it.isNullOrBlank() }.distinct().joinToString(" ").replaceFirstChar { it.uppercase() }

        // Si la app se cerró por un error, se guarda y se informa al abrirla de nuevo.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                sp?.edit()?.putString("cierre", describe(e) + "\n" + e.stackTraceToString().take(600))?.commit()
            }
            previous?.uncaughtException(thread, e)
        }
        sp?.getString("cierre", null)?.let {
            sp?.edit()?.remove("cierre")?.apply()
            report("Cierre inesperado", it)
        }
    }

    /** Anota un error. [kind]: qué se estaba haciendo ("Activar código", "Reproducir"...). */
    fun report(kind: String, detail: String) {
        if (!Cloud.enabled) return
        val prefs = sp ?: return
        val now = System.currentTimeMillis()
        val day = now / 86_400_000L
        val key = (kind + "|" + detail.take(120)).hashCode().toString()
        synchronized(this) {
            if (now - prefs.getLong("ultimo_$key", 0) < SAME_ERROR_GAP_MS) return
            val count = if (prefs.getLong("dia", -1) == day) prefs.getInt("cuantos", 0) else 0
            if (count >= MAX_PER_DAY) return
            prefs.edit().putLong("dia", day).putInt("cuantos", count + 1).putLong("ultimo_$key", now).apply()
        }
        val fields = JSONObject()
            .put("quien", str(who.ifBlank { "Sin activar" }))
            .put("modelo", str(model))
            .put("version", JSONObject().put("integerValue", BuildConfig.VERSION_CODE.toString()))
            .put("fecha", JSONObject().put("integerValue", now.toString()))
            .put("tipo", str(kind.take(40)))
            .put("detalle", str(detail.take(MAX_DETAIL)))
        scope.launch {
            runCatching {
                Cloud.http("POST", Cloud.API + path, JSONObject().put("fields", fields).toString())
            }
        }
    }

    fun report(kind: String, e: Throwable) = report(kind, describe(e))

    /** Texto corto y útil de un error: tipo, mensaje y respuesta del servidor si la hay. */
    fun describe(e: Throwable): String {
        val parts = mutableListOf<String>()
        var t: Throwable? = e
        var depth = 0
        while (t != null && depth < 3) {
            parts += when (t) {
                is HttpError -> "HTTP ${t.code}" + (t.detail.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "")
                else -> t.javaClass.simpleName + (t.message?.takeIf { it.isNotBlank() }?.let { ": ${it.take(200)}" } ?: "")
            }
            t = t.cause?.takeIf { it !== t }
            depth++
        }
        return parts.joinToString(" ← ")
    }

    // ---- Administrador ----

    /** Los últimos errores informados, el más nuevo primero. Bloquea. */
    fun list(): List<ErrorReport> {
        val out = mutableListOf<ErrorReport>()
        var token: String? = null
        do {
            val url = buildString {
                append(Cloud.API).append(path).append("?pageSize=300")
                token?.let { append("&pageToken=").append(URLEncoder.encode(it, "UTF-8")) }
            }
            val res = try {
                JSONObject(Cloud.http("GET", url))
            } catch (e: HttpError) {
                if (e.code == 404) return emptyList() else throw e
            }
            res.optJSONArray("documents")?.let { docs ->
                for (i in 0 until docs.length()) {
                    val d = docs.getJSONObject(i)
                    val f = d.optJSONObject("fields") ?: continue
                    out += ErrorReport(
                        id = d.optString("name").substringAfterLast('/'),
                        who = f.s("quien"),
                        model = f.s("modelo"),
                        version = f.n("version").toInt(),
                        time = f.n("fecha"),
                        kind = f.s("tipo"),
                        detail = f.s("detalle"),
                    )
                }
            }
            token = res.optString("nextPageToken").ifBlank { null }
        } while (token != null && out.size < 1000)
        return out.sortedByDescending { it.time }
    }

    /** Borra los errores indicados (para limpiar la lista). Bloquea. */
    fun delete(ids: List<String>) {
        ids.chunked(400).forEach { chunk ->
            if (chunk.isEmpty()) return@forEach
            Cloud.commit(*chunk.map { JSONObject().put("delete", "$path/$it") }.toTypedArray())
        }
    }

    private fun str(v: String) = JSONObject().put("stringValue", v)
    private fun JSONObject.s(k: String) = optJSONObject(k)?.optString("stringValue").orEmpty()
    private fun JSONObject.n(k: String) = optJSONObject(k)?.optString("integerValue")?.toLongOrNull() ?: 0L
}
