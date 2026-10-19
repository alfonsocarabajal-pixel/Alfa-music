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
) {
    val pretty: String get() = "ALFA-${code.take(4)}-${code.drop(4)}"
}

/**
 * Control de acceso con códigos de invitación, guardados en Firestore.
 * La seguridad la ponen las reglas de Firebase: solo quien conoce la clave de
 * administrador (que no está dentro de la app) puede crear o revocar códigos.
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

    val isAdmin: Boolean get() = adminKey.isNotBlank()

    /** Si Firebase no está configurado, la app no pide código. */
    val activated: Boolean get() = !Cloud.enabled || code.isNotBlank() || isAdmin

    fun clear() {
        sp.edit().clear().apply()
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
                val c = norm.removePrefix("ALFA")
                if (c.length != 8) return Result.Rejected("El código tiene que ser como ALFA-XXXX-XXXX.")
                val inv = getInvite(c) ?: return Result.Rejected("Ese código no existe o fue revocado.")
                when (inv.device) {
                    deviceId -> Unit
                    "" -> claim(c)
                    else -> return Result.Rejected("Ese código ya se usó en otro teléfono. Pedile uno nuevo a quien te invitó.")
                }
                code = c
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
     * Sin internet, deja pasar (ya estaba activado antes).
     */
    fun stillValid(): Boolean = try {
        when {
            !Cloud.enabled -> true
            isAdmin -> { listIssued(adminKey); true }
            code.isBlank() -> false
            else -> getInvite(code)?.device == deviceId
        }
    } catch (e: HttpError) {
        !(e.code == 403 || e.code == 404)
    } catch (e: IOException) {
        true
    }

    private fun normalize(s: String) = s.uppercase().filter { it in ALPHABET || it in 'A'..'Z' || it in '0'..'9' }

    private fun invitePath(c: String) = "${Cloud.groupPath}/invitaciones/$c"
    private fun issuedPath(key: String, c: String = "") =
        "${Cloud.groupPath}/admins/$key/emitidas" + if (c.isNotBlank()) "/$c" else ""

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
                .put("updateMask", JSONObject().put("fieldPaths", JSONArray(listOf("dispositivo", "modelo", "activada"))))
                .put("currentDocument", JSONObject().put("exists", true))
        )
    }

    private fun parseInvite(c: String, f: JSONObject?): Invite = Invite(
        code = c,
        name = f?.str("nombre").orEmpty(),
        created = f?.long("creada") ?: 0,
        revoked = f?.long("revocada") ?: 0,
        device = f?.str("dispositivo").orEmpty(),
        model = f?.str("modelo").orEmpty(),
        activated = f?.long("activada") ?: 0,
    )

    // ---- Administrador ----

    private fun listIssued(key: String): List<Invite> {
        val res = JSONObject(Cloud.http("GET", Cloud.API + issuedPath(key) + "?pageSize=300"))
        val docs = res.optJSONArray("documents") ?: return emptyList()
        return (0 until docs.length()).map { i ->
            val d = docs.getJSONObject(i)
            parseInvite(d.optString("name").substringAfterLast('/'), d.optJSONObject("fields"))
        }
    }

    /** Todas las invitaciones, con su estado de activación. La más nueva primero. */
    fun listInvites(): List<Invite> = listIssued(adminKey).map { issued ->
        if (issued.revoked > 0) issued
        else {
            val live = runCatching { getInvite(issued.code) }.getOrNull()
            if (live == null) issued
            else issued.copy(device = live.device, model = live.model, activated = live.activated)
        }
    }.sortedByDescending { it.created }

    fun createInvite(name: String): Invite {
        repeat(5) {
            val c = String(CharArray(8) { ALPHABET[Cloud.random.nextInt(ALPHABET.length)] })
            val now = System.currentTimeMillis()
            val n = name.trim().take(40).ifBlank { "Sin nombre" }
            try {
                Cloud.commit(
                    JSONObject().put(
                        "update", JSONObject().put("name", issuedPath(adminKey, c)).put(
                            "fields", JSONObject()
                                .put("nombre", str(n))
                                .put("creada", int(now))
                                .put("revocada", int(0))
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
                        )
                    ).put("currentDocument", JSONObject().put("exists", false)),
                )
                return Invite(c, n, now, 0, "", "", 0)
            } catch (e: HttpError) {
                if (e.code != 400 && e.code != 409) throw e // 409/400: el código ya existía, se prueba otro
            }
        }
        throw IOException("No se pudo crear el código")
    }

    fun revoke(c: String) {
        Cloud.commit(
            JSONObject()
                .put(
                    "update", JSONObject().put("name", issuedPath(adminKey, c))
                        .put("fields", JSONObject().put("revocada", int(System.currentTimeMillis())))
                )
                .put("updateMask", JSONObject().put("fieldPaths", JSONArray(listOf("revocada")))),
            JSONObject().put("delete", invitePath(c)),
        )
    }

    companion object {
        /** Sin letras ni números que se confundan (0/O, 1/I/L). */
        const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
    }
}
