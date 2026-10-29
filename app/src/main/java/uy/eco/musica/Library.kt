package uy.eco.musica

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Una playlist compartida por el grupo. [id] vacío = todavía no se guardó. */
data class Playlist(
    val id: String,
    val name: String,
    val author: String,
    val updated: Long,
    val songs: List<Song>,
)

/** Formato compacto para guardar canciones en el teléfono. */
fun Song.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("t", title)
    .put("a", artist)
    .put("d", durationSec)
    .put("th", thumb ?: "")
    .put("art", art ?: "")

fun songFromJson(o: JSONObject): Song? {
    val id = o.optString("id").takeIf { it.length == 11 } ?: return null
    return Song(
        id = id,
        title = o.optString("t"),
        artist = o.optString("a"),
        durationSec = o.optLong("d"),
        thumb = o.optString("th").ifBlank { null },
        art = o.optString("art").ifBlank { null },
    )
}

/** Preferencias simples guardadas en el teléfono. */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("alfa", Context.MODE_PRIVATE)

    /** Nombre con el que firmás las playlists. */
    var author: String
        get() = sp.getString("autor", "").orEmpty()
        set(v) = sp.edit().putString("autor", v.trim()).apply()

    /** La playlist compartida que está cargada en la cola (para poder actualizarla). */
    var loadedId: String
        get() = sp.getString("pl_id", "").orEmpty()
        set(v) = sp.edit().putString("pl_id", v).apply()

    var loadedName: String
        get() = sp.getString("pl_nombre", "").orEmpty()
        set(v) = sp.edit().putString("pl_nombre", v).apply()

    var loadedAuthor: String
        get() = sp.getString("pl_autor", "").orEmpty()
        set(v) = sp.edit().putString("pl_autor", v).apply()
}

/** La última cola de este teléfono, para recuperarla al abrir la app. */
class QueueState(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("cola", Context.MODE_PRIVATE)

    class Saved(val songs: List<Pair<Song, Boolean>>, val index: Int, val positionMs: Long, val shuffle: Boolean)

    fun save(songs: List<Pair<Song, Boolean>>, index: Int, positionMs: Long, shuffle: Boolean) {
        val arr = JSONArray()
        songs.forEach { (s, radio) -> arr.put(s.toJson().put("r", radio)) }
        val o = JSONObject()
            .put("canciones", arr)
            .put("i", index)
            .put("pos", positionMs)
            .put("aleatorio", shuffle)
        sp.edit().putString("estado", o.toString()).apply()
    }

    fun load(): Saved? = runCatching {
        val o = JSONObject(sp.getString("estado", null) ?: return null)
        val arr = o.getJSONArray("canciones")
        val songs = (0 until arr.length()).mapNotNull { i ->
            val j = arr.getJSONObject(i)
            // Las emisoras de radio también se recuperan (su id empieza con "emisora:").
            (songFromJson(j) ?: j.optString("id").takeIf { it.startsWith("emisora:") }
                ?.let { Song(it, j.optString("t"), j.optString("a"), 0, null, null) })
                ?.let { it to j.optBoolean("r") }
        }
        if (songs.isEmpty()) return null
        Saved(
            songs = songs,
            index = o.optInt("i").coerceIn(0, songs.size - 1),
            positionMs = o.optLong("pos").coerceAtLeast(0),
            shuffle = o.optBoolean("aleatorio"),
        )
    }.getOrNull()
}
