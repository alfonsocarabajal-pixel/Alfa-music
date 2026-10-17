package uy.eco.musica

import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.net.HttpURLConnection
import java.net.URL

/** Cliente HTTP mínimo que usa el extractor para hablar con YouTube. Sin librerías extra. */
object Http : Downloader() {

    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"

    override fun execute(request: Request): Response {
        val conn = URL(request.url()).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.instanceFollowRedirects = true
            conn.requestMethod = request.httpMethod()
            conn.setRequestProperty("User-Agent", USER_AGENT)

            request.headers().forEach { (name, values) ->
                // Android descomprime gzip solo si no pedimos la codificación a mano.
                if (name.equals("Accept-Encoding", ignoreCase = true)) return@forEach
                values.forEachIndexed { i, value ->
                    if (i == 0) conn.setRequestProperty(name, value)
                    else conn.addRequestProperty(name, value)
                }
            }

            request.dataToSend()?.let { data ->
                conn.doOutput = true
                conn.outputStream.use { it.write(data) }
            }

            val code = conn.responseCode
            if (code == 429) {
                throw ReCaptchaException("YouTube pidió verificación", request.url())
            }

            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""

            val headers = buildMap<String, List<String>> {
                conn.headerFields.forEach { (k, v) -> if (k != null && v != null) put(k, v) }
            }

            return Response(code, conn.responseMessage, headers, body, conn.url.toString())
        } finally {
            conn.disconnect()
        }
    }
}
