package uy.eco.musica

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import uy.eco.musica.Cloud.int
import uy.eco.musica.Cloud.long
import uy.eco.musica.Cloud.str
import java.io.IOException

/** Una invitación emitida por el administrador. */
data class Invite(
    val code: String,
    val name: String,
    val created: Long,
    val revoked: Long,
    val device: String,
    val model: String,
    val activated: Long,
    val group: String,
) {
    val pretty: String get() = code
}

/** Un grupo: cada uno tiene sus propias playlists. El nombre solo lo ve el administrador. */
data class Group(val id: String, val name: String)

/**
 * Control de acceso con códigos de invitación y grupos, guardados en Firestore.
 * La seguridad la ponen las reglas de Firebase: solo quien conoce la clave de
 * administrador (que no está dentro de la app) puede crear, mover o revocar códigos.
 */
class Access(context: Context) {

    private val sp = context.applicationContext.getSharedPreferences("acceso", Context.MODE_PRIVATE)

    @SuppressLint("HardwareIds")
    val deviceId: String = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        ?.takeIf { it.isNotBlank() } ?: "sin-id"

    val model: String = listOf(Build.MANUFACTURER, Build.MODEL)
        .filter { !it.isNullOrBlank() }.distinct().joinToString(" ").replaceFirstChar { it.uppercase() }

    /** Código de invitación activado en este teléfono. */
    var code: String
        get() = sp.getString("codigo", "").orEmpty()
        private set(v) = sp.edit().putString("codigo", v).apply()

    /** Clave de administrador (solo en el teléfono del administrador). */
    var adminKey: String
        get() = sp.getString("admin", "").orEmpty()
        private set(v) = sp.edit().putString("admin", v).apply()

    /** Grupo cuyas playlists se ven en este teléfono. */
    var groupId: String
        get() = sp.getString("grupo", MAIN_GROUP).orEmpty().ifBlank { MAIN_GROUP }
        set(v) {
            sp.edit().putString("grupo", v).apply()
            Cloud.groupId = v
        }

    val isAdmin: Boolean get() = adminKey.isNotBlank()

    /** Si Firebase no está configurado, la app no pide código. */
    val activated: Boolean get() = !Cloud.enabled || code.isNotBlank() || isAdmin

    init {
        Cloud.groupId = groupId
    }

    fun clear() {
        sp.edit().clear().apply()
        Cloud.groupId = MAIN_GROUP
    }

    sealed interface Result {
        data object Ok : Result
        data class Rejected(val message: String) : Result
        data object Offline : Result
    }

    // ---- Activación (bloquea: llamar fuera del hilo principal) ----

    fun activate(input: String): Result {
        val norm = normalize(input)
        return try {
            if (norm.startsWith("ADMIN")) {
                listIssued(norm) // falla con 403 si la clave no es la correcta
                adminKey = norm
                Result.Ok
            } else {
                val c = norm
                if (c.length !in 3..16) return Result.Rejected("Revisá el código: es una palabra con dos números, como LUNA27.")
                val inv = getInvite(c) ?: return Result.Rejected("Ese código no existe o fue revocado.")
                when (inv.device) {
                    deviceId -> Unit
                    "" -> claim(c)
                    else -> return Result.Rejected("Ese código ya se usó en otro teléfono. Pedile uno nuevo a quien te invitó.")
                }
                code = c
                groupId = inv.group
                Result.Ok
            }
        } catch (e: HttpError) {
            if (e.code == 403 || e.code == 404) Result.Rejected("Código incorrecto.")
            else Result.Rejected("No se pudo verificar el código (error ${e.code}). Probá de nuevo.")
        } catch (e: IOException) {
            Result.Offline
        }
    }

    /**
     * Revisión al abrir la app: devuelve false solo si el acceso fue revocado.
     * También aplica un cambio de grupo hecho por el administrador.
     * Sin internet, deja pasar (ya estaba activado antes).
     */
    fun stillValid(): Boolean = try {
        when {
            !Cloud.enabled -> true
            isAdmin -> { listIssued(adminKey); true }
            code.isBlank() -> false
            else -> {
                val inv = getInvite(code)
                if (inv != null && inv.device == deviceId) {
                    if (inv.group != groupId) groupId = inv.group
                    true
                } else false
            }
        }
    } catch (e: HttpError) {
        !(e.code == 403 || e.code == 404)
    } catch (e: IOException) {
        true
    }

    private fun normalize(s: String) = s.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }

    private fun invitePath(c: String) = "${Cloud.groupPath}/invitaciones/$c"
    private fun adminPath(key: String, collection: String, id: String = "") =
        "${Cloud.groupPath}/admins/$key/$collection" + if (id.isNotBlank()) "/$id" else ""

    private fun getInvite(c: String): Invite? = try {
        parseInvite(c, JSONObject(Cloud.http("GET", Cloud.API + invitePath(c))).optJSONObject("fields"))
    } catch (e: HttpError) {
        if (e.code == 404) null else throw e
    }

    private fun claim(c: String) {
        val fields = JSONObject()
            .put("dispositivo", str(deviceId))
            .put("modelo", str(model))
            .put("activada", int(System.currentTimeMillis()))
        Cloud.commit(
            JSONObject()
                .put("update", JSONObject().put("name", invitePath(c)).put("fields", fields))
                .put("updateMask", mask("dispositivo", "modelo", "activada"))
                .put("currentDocument", JSONObject().put("exists", true))
        )
    }

    private fun mask(vararg fields: String) = JSONObject().put("fieldPaths", JSONArray(fields.toList()))

    private fun parseInvite(c: String, f: JSONObject?): Invite = Invite(
        code = c,
        name = f?.str("nombre").orEmpty(),
        created = f?.long("creada") ?: 0,
        revoked = f?.long("revocada") ?: 0,
        device = f?.str("dispositivo").orEmpty(),
        model = f?.str("modelo").orEmpty(),
        activated = f?.long("activada") ?: 0,
        group = f?.str("grupo").orEmpty().ifBlank { MAIN_GROUP }, // las invitaciones viejas son del grupo principal
    )

    private fun listDocs(path: String): List<Pair<String, JSONObject?>> {
        val res = JSONObject(Cloud.http("GET", Cloud.API + path + "?pageSize=300"))
        val docs = res.optJSONArray("documents") ?: return emptyList()
        return (0 until docs.length()).map { i ->
            val d = docs.getJSONObject(i)
            d.optString("name").substringAfterLast('/') to d.optJSONObject("fields")
        }
    }

    // ---- Administrador ----

    private fun listIssued(key: String): List<Invite> =
        listDocs(adminPath(key, "emitidas")).map { (id, f) -> parseInvite(id, f) }

    /** Todas las invitaciones, con su estado de activación. La más nueva primero. */
    fun listInvites(): List<Invite> = listIssued(adminKey).map { issued ->
        if (issued.revoked > 0) issued
        else {
            val live = runCatching { getInvite(issued.code) }.getOrNull()
            if (live == null) issued
            else issued.copy(device = live.device, model = live.model, activated = live.activated, group = live.group)
        }
    }.sortedByDescending { it.created }

    /** Los grupos, con el principal primero. */
    fun listGroups(): List<Group> {
        val groups = listDocs(adminPath(adminKey, "grupos"))
            .map { (id, f) -> Group(id, f?.str("nombre").orEmpty().ifBlank { "Sin nombre" }) }
        val main = groups.firstOrNull { it.id == MAIN_GROUP } ?: Group(MAIN_GROUP, "Principal")
        return listOf(main) + groups.filter { it.id != MAIN_GROUP }.sortedBy { it.name.lowercase() }
    }

    fun createGroup(name: String): Group {
        val id = String(CharArray(12) { GROUP_CHARS[Cloud.random.nextInt(GROUP_CHARS.length)] })
        val n = name.trim().take(40).ifBlank { "Sin nombre" }
        Cloud.commit(
            JSONObject().put(
                "update", JSONObject().put("name", adminPath(adminKey, "grupos", id))
                    .put("fields", JSONObject().put("nombre", str(n)))
            ).put("currentDocument", JSONObject().put("exists", false))
        )
        return Group(id, n)
    }

    fun createInvite(name: String, group: String): Invite {
        repeat(5) {
            val c = WORDS[Cloud.random.nextInt(WORDS.size)] + (10 + Cloud.random.nextInt(90))
            val now = System.currentTimeMillis()
            val n = name.trim().take(40).ifBlank { "Sin nombre" }
            try {
                Cloud.commit(
                    JSONObject().put(
                        "update", JSONObject().put("name", adminPath(adminKey, "emitidas", c)).put(
                            "fields", JSONObject()
                                .put("nombre", str(n))
                                .put("creada", int(now))
                                .put("revocada", int(0))
                                .put("grupo", str(group))
                        )
                    ).put("currentDocument", JSONObject().put("exists", false)),
                    JSONObject().put(
                        "update", JSONObject().put("name", invitePath(c)).put(
                            "fields", JSONObject()
                                .put("nombre", str(n))
                                .put("creada", int(now))
                                .put("dispositivo", str(""))
                                .put("modelo", str(""))
                                .put("activada", int(0))
                                .put("grupo", str(group))
                        )
                    ).put("currentDocument", JSONObject().put("exists", false)),
                )
                return Invite(c, n, now, 0, "", "", 0, group)
            } catch (e: HttpError) {
                if (e.code != 400 && e.code != 409) throw e // 409/400: el código ya existía, se prueba otro
            }
        }
        throw IOException("No se pudo crear el código")
    }

    /** Cambia a alguien de grupo. Se aplica la próxima vez que abra la app. */
    fun moveInvite(c: String, group: String) {
        val g = JSONObject().put("grupo", str(group))
        Cloud.commit(
            JSONObject()
                .put("update", JSONObject().put("name", adminPath(adminKey, "emitidas", c)).put("fields", g))
                .put("updateMask", mask("grupo")),
            JSONObject()
                .put("update", JSONObject().put("name", invitePath(c)).put("fields", g))
                .put("updateMask", mask("grupo"))
                .put("currentDocument", JSONObject().put("exists", true)),
        )
    }

    fun revoke(c: String) {
        Cloud.commit(
            JSONObject()
                .put(
                    "update", JSONObject().put("name", adminPath(adminKey, "emitidas", c))
                        .put("fields", JSONObject().put("revocada", int(System.currentTimeMillis())))
                )
                .put("updateMask", mask("revocada")),
            JSONObject().put("delete", invitePath(c)),
        )
    }

    companion object {
        /** El grupo original (el de las playlists que ya existían). */
        const val MAIN_GROUP = "principal"
        private const val GROUP_CHARS = "abcdefghijkmnpqrstuvwxyz23456789"

        /** Palabras cortas y fáciles de recordar para los códigos (sin tildes ni Ñ). */
        val WORDS = listOf(
            "SOL", "LUNA", "MAR", "RIO", "NUBE", "ROCK", "JAZZ", "SALSA", "TANGO", "BLUES",
            "FUNK", "DISCO", "PIANO", "BAJO", "VOZ", "RITMO", "BAILE", "FIESTA", "PLAYA", "ISLA",
            "VIENTO", "FUEGO", "TIGRE", "LEON", "LOBO", "OSO", "GATO", "PUMA", "PANDA", "KOALA",
            "MANGO", "LIMON", "COCO", "KIWI", "MENTA", "CAFE", "MATE", "TORTA", "OLA", "DUNA",
            "SELVA", "CIELO", "ROSA", "MAGIA", "ECO", "RADIO", "BOMBO", "CUMBIA", "MURGA", "CANDOMBE",
            "TRUENO", "RAYO", "NIEVE", "LAGO", "MONTE", "PAMPA", "COMETA", "PLANETA", "ROBOT", "NINJA",
        )
    }
}
