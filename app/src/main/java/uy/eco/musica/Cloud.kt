package uy.eco.musica

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.SecureRandom

/**
 * Playlists compartidas del grupo, guardadas en Firebase Firestore mediante su API REST.
 * Sin SDK de Firebase: así la app sigue pesando muy poco.
 * Todas las funciones bloquean: llamarlas fuera del hilo principal.
 */
/** Error HTTP del servidor (404 = no existe, 403 = sin permiso). */
class HttpError(val code: Int) : IOException("Error $code del servidor")

object Cloud {

    private val project = BuildConfig.FS_PROJECT
    private val group = BuildConfig.FS_GROUP

    val enabled: Boolean get() = project.isNotBlank() && group.isNotBlank()

    internal val docsRoot get() = "projects/$project/databases/(default)/documents"
    internal val groupPath get() = "$docsRoot/grupos/$group"

    /** Grupo activo. Lo fija [Access]; la interfaz lo observa para recargar las playlists. */
    var groupId by mutableStateOf("principal")

    // El grupo principal conserva la ubicación original, así no hubo que mover nada.
    private val collectionPath get() = collectionFor(groupId)

    private fun collectionFor(group: String) =
        if (group == "principal") "$groupPath/playlists" else "$groupPath/equipos/$group/playlists"
    internal const val API = "https://firestore.googleapis.com/v1/"

    /** Todas las playlists del grupo, la más reciente primero. */
    fun list(): List<Playlist> {
        val out = mutableListOf<Playlist>()
        var token: String? = null
        do {
            val url = buildString {
                append(API).append(collectionPath).append("?pageSize=300")
                token?.let { append("&pageToken=").append(URLEncoder.encode(it, "UTF-8")) }
            }
            val res = JSONObject(http("GET", url))
            res.optJSONArray("documents")?.let { docs ->
                for (i in 0 until docs.length()) parse(docs.getJSONObject(i))?.let(out::add)
            }
            token = res.optString("nextPageToken").ifBlank { null }
        } while (token != null)
        return out.sortedByDescending { it.updated }
    }

    /** Una playlist tal como está ahora en el servidor (null si ya no existe). */
    fun get(id: String): Playlist? = try {
        parse(JSONObject(http("GET", "$API$collectionPath/$id")))
    } catch (e: HttpError) {
        if (e.code == 404) null else throw e
    }

    /** Crea la playlist (si no tiene id) o reemplaza la existente. Devuelve la versión guardada. */
    /** Guarda una copia independiente de la playlist en otro grupo (solo lo usa el administrador). */
    fun copyToGroup(p: Playlist, group: String): Playlist = save(p.copy(id = ""), group)

    fun save(p: Playlist, group: String = groupId): Playlist {
        val saved = p.copy(
            id = p.id.ifBlank { newId() },
            name = p.name.trim().take(80).ifBlank { "Sin nombre" },
            author = p.author.trim().take(40),
            updated = System.currentTimeMillis(),
            songs = p.songs.distinctBy { it.id }.take(500),
        )
        val write = JSONObject().put(
            "update",
            JSONObject()
                .put("name", "${collectionFor(group)}/${saved.id}")
                .put("fields", fields(saved))
        )
        commit(write)
        return saved
    }

    fun delete(id: String) {
        commit(JSONObject().put("delete", "$collectionPath/$id"))
    }

    // ---- Formato de Firestore ----

    internal fun str(v: String) = JSONObject().put("stringValue", v)
    internal fun int(v: Long) = JSONObject().put("integerValue", v.toString())
    internal fun map(f: JSONObject) = JSONObject().put("mapValue", JSONObject().put("fields", f))

    private fun fields(p: Playlist): JSONObject {
        val songs = JSONArray()
        p.songs.forEach { s ->
            songs.put(
                map(
                    JSONObject()
                        .put("id", str(s.id))
                        .put("t", str(s.title))
                        .put("a", str(s.artist))
                        .put("d", int(s.durationSec))
                        .put("th", str(s.thumb.orEmpty()))
                        .put("art", str(s.art.orEmpty()))
                )
            )
        }
        return JSONObject()
            .put("nombre", str(p.name))
            .put("autor", str(p.author))
            .put("actualizada", int(p.updated))
            .put("canciones", JSONObject().put("arrayValue", JSONObject().put("values", songs)))
    }

    internal fun JSONObject.str(key: String): String =
        optJSONObject(key)?.optString("stringValue").orEmpty()

    internal fun JSONObject.long(key: String): Long =
        optJSONObject(key)?.let { it.optString("integerValue").toLongOrNull() ?: it.optLong("doubleValue") } ?: 0L

    private fun parse(doc: JSONObject): Playlist? {
        val id = doc.optString("name").substringAfterLast('/').ifBlank { return null }
        val f = doc.optJSONObject("fields") ?: return null
        val values = f.optJSONObject("canciones")?.optJSONObject("arrayValue")?.optJSONArray("values")
        val songs = buildList {
            if (values != null) for (i in 0 until values.length()) {
                val s = values.optJSONObject(i)?.optJSONObject("mapValue")?.optJSONObject("fields") ?: continue
                val songId = s.str("id").takeIf { it.length == 11 } ?: continue
                add(
                    Song(
                        id = songId,
                        title = s.str("t"),
                        artist = s.str("a"),
                        durationSec = s.long("d"),
                        thumb = s.str("th").ifBlank { null },
                        art = s.str("art").ifBlank { null },
                    )
                )
            }
        }
        return Playlist(
            id = id,
            name = f.str("nombre").ifBlank { "Sin nombre" },
            author = f.str("autor"),
            updated = f.long("actualizada"),
            songs = songs,
        )
    }

    // ---- Red ----

    internal fun commit(vararg writes: JSONObject) {
        val arr = JSONArray()
        writes.forEach { arr.put(it) }
        http("POST", "$API$docsRoot:commit", JSONObject().put("writes", arr).toString())
    }

    internal fun http(method: String, url: String, body: String? = null): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.requestMethod = method
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code >= 400) throw HttpError(code)
            return text
        } finally {
            conn.disconnect()
        }
    }

    internal val random = SecureRandom()
    private const val CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    private fun newId(): String = String(CharArray(20) { CHARS[random.nextInt(CHARS.length)] })
}
