package uy.eco.musica

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Actualizaciones dentro de la app: busca la última versión publicada en GitHub,
 * la descarga y la instala encima, sin perder nada.
 * La primera vez Android pide permitir que la app instale; después, cada
 * actualización se confirma con un toque en el instalador de Android.
 */
object Updater {

    private const val LATEST =
        "https://api.github.com/repos/alfonsocarabajal-pixel/Alfa-music/releases/latest"

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
     * Descarga el APK al almacenamiento interno de la app. [onProgress] recibe de 0 a 1.
     * Bloquea: llamar fuera del hilo principal. Devuelve el archivo descargado.
     */
    fun download(context: Context, release: Release, onProgress: (Float) -> Unit): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // descargas viejas
        val file = File(dir, "AlfaMusic-${release.version}.apk")
        val conn = URL(release.apkUrl).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            if (conn.responseCode != 200) throw IOException("Error ${conn.responseCode} al descargar")
            val total = conn.contentLengthLong
            var done = 0L
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }
            if (total > 0 && done != total) throw IOException("La descarga quedó incompleta")
        } catch (e: Exception) {
            file.delete()
            throw e
        } finally {
            conn.disconnect()
        }
        return file
    }

    /**
     * Abre el instalador estándar de Android con el APK descargado (el mismo que se usa al
     * instalar desde el navegador). Funciona también en teléfonos Xiaomi, que bloquean
     * el método de instalación silenciosa.
     */
    fun openInstaller(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
