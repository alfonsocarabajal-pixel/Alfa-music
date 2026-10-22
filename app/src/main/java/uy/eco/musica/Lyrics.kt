package uy.eco.musica

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

/** Una línea de letra con el momento en que empieza (en milisegundos). */
data class LyricLine(val timeMs: Long, val text: String)

/**
 * Letras sincronizadas desde LRCLIB (base abierta y gratuita, sin cuenta ni clave).
 * Para un video karaoke se busca la canción original: se limpia el título
 * ("(Karaoke Version)", "Instrumental", etc.) y, si no aparece, se usa lo que se buscó.
 */
object Lyrics {

    private const val API = "https://lrclib.net/api/search?q="

    /** Busca la letra sincronizada. Devuelve null si no hay. Bloquea. */
    fun find(title: String, artist: String, typedQuery: String?, durationSec: Long): List<LyricLine>? {
        val queries = listOfNotNull(
            cleanTitle(title).takeIf { it.length >= 3 },
            typedQuery?.trim()?.takeIf { it.length >= 2 },
        ).distinct()
        for (q in queries) {
            val lines = runCatching { search(q, durationSec) }.getOrNull()
            if (!lines.isNullOrEmpty()) return lines
        }
        return null
    }

    /** Quita del título de un video karaoke todo lo que no es "Artista - Canción". */
    fun cleanTitle(raw: String): String {
        var t = raw
        // Lo que va entre paréntesis o corchetes suele ser "(Karaoke Version)", "[Instrumental]", etc.
        t = t.replace(Regex("""[\(\[\{][^\)\]\}]*[\)\]\}]"""), " ")
        // Lo que viene después de "|" suele ser el nombre del canal.
        t = t.substringBefore('|')
        val noise = listOf(
            "karaoke version", "karaoke", "instrumental version", "instrumental", "with lyrics",
            "lyrics", "lyric video", "letra", "pista", "sing king", "backing track", "official",
            "versión", "version", "cover", "hd", "4k",
        )
        noise.forEach { t = t.replace(Regex("(?i)\\b" + Regex.escape(it) + "\\b"), " ") }
        return t.replace(Regex("""[\s\-–—_:]+$"""), "").replace(Regex("""\s+"""), " ").trim()
    }

    private fun search(q: String, durationSec: Long): List<LyricLine>? {
        val conn = URL(API + URLEncoder.encode(q, "UTF-8")).openConnection() as HttpURLConnection
        val body = try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", "AlfaMusic/1.0 (app personal)")
            if (conn.responseCode != 200) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
        val arr = JSONArray(body)
        val candidates = (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { it.optString("syncedLyrics").isNotBlank() && !it.optBoolean("instrumental") }
        if (candidates.isEmpty()) return null
        // Si se conoce la duración, se prefiere la versión de duración más parecida.
        val best = if (durationSec > 0) candidates.minBy { abs(it.optDouble("duration", 0.0) - durationSec) }
        else candidates.first()
        return parse(best.optString("syncedLyrics")).ifEmpty { null }
    }

    /** Formato LRC: "[mm:ss.xx] texto". */
    fun parse(lrc: String): List<LyricLine> {
        val tag = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
        return lrc.lineSequence().flatMap { line ->
            val times = tag.findAll(line).toList()
            if (times.isEmpty()) return@flatMap emptySequence()
            val text = line.substring(times.last().range.last + 1).trim()
            times.asSequence().map { m ->
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val frac = m.groupValues[3]
                val ms = when (frac.length) {
                    0 -> 0L
                    1 -> frac.toLong() * 100
                    2 -> frac.toLong() * 10
                    else -> frac.take(3).toLong()
                }
                LyricLine(min * 60_000 + sec * 1000 + ms, text)
            }
        }.sortedBy { it.timeMs }.toList()
    }
}
