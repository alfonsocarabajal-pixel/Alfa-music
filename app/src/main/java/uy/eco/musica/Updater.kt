package uy.eco.musica

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Actualizaciones dentro de la app: busca la última versión publicada en GitHub,
 * la descarga y la instala encima, sin perder nada.
 * La primera vez Android pide confirmar; desde Android 12, cuando la app ya se
 * actualizó a sí misma, las siguientes pueden instalarse sin preguntar.
 */
object Updater {

    private const val LATEST =
        "https://api.github.com/repos/alfonsocarabajal-pixel/Alfa-music/releases/latest"
    private const val ACTION_STATUS = "uy.eco.musica.INSTALL_STATUS"

    data class Release(val version: Int, val apkUrl: String)

    /** Devuelve la versión nueva si hay una más reciente que la instalada. Bloquea. */
    fun check(): Release? {
        val conn = URL(LATEST).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            if (conn.responseCode != 200) return null
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val version = json.optString("tag_name").removePrefix("v").toIntOrNull() ?: return null
            val assets = json.optJSONArray("assets") ?: return null
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") }
                ?.optString("browser_download_url")?.ifBlank { null } ?: return null
            if (version > BuildConfig.VERSION_CODE) Release(version, apk) else null
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    /** Número de la última versión publicada (null si no se pudo consultar). Bloquea. */
    fun latestVersion(): Int? {
        val conn = URL(LATEST).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            if (conn.responseCode != 200) return null
            JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                .optString("tag_name").removePrefix("v").toIntOrNull()
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    /** ¿Android ya le permite a la app instalar actualizaciones? */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Abre el ajuste de Android para permitir que Alfa Music instale actualizaciones. */
    fun openInstallPermission(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /**
     * Descarga el APK directo a una sesión de instalación de Android y la confirma.
     * [onProgress] recibe de 0 a 1. Bloquea: llamar fuera del hilo principal.
     */
    fun downloadAndInstall(context: Context, release: Release, onProgress: (Float) -> Unit) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)
        try {
            val conn = URL(release.apkUrl).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.instanceFollowRedirects = true
                if (conn.responseCode != 200) throw IOException("Error ${conn.responseCode} al descargar")
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    session.openWrite("AlfaMusic.apk", 0, if (total > 0) total else -1).use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) onProgress(done.toFloat() / total)
                        }
                        session.fsync(out)
                    }
                }
            } finally {
                conn.disconnect()
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val status = PendingIntent.getBroadcast(
                context, sessionId,
                Intent(context, InstallReceiver::class.java).setAction(ACTION_STATUS),
                flags,
            )
            session.commit(status.intentSender)
        } catch (e: Exception) {
            session.abandon()
            throw e
        } finally {
            session.close()
        }
    }

    /** Recibe el resultado de la instalación; si Android pide confirmar, muestra su pantalla. */
    class InstallReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    @Suppress("DEPRECATION")
                    val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                PackageInstaller.STATUS_SUCCESS -> Unit // la app se reinicia con la versión nueva
                else -> Toast.makeText(
                    context,
                    "No se pudo instalar la actualización. Probá de nuevo más tarde.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }
}
