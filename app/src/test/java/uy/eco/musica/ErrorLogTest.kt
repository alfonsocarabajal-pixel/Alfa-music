package uy.eco.musica

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ErrorLogTest {
    @Test
    fun describeMuestraElMotivoDelServidor() {
        val d = ErrorLog.describe(HttpError(403, "PERMISSION_DENIED: Missing or insufficient permissions."))
        assertTrue(d, d.startsWith("HTTP 403: PERMISSION_DENIED"))
    }

    @Test
    fun describeIncluyeLaCausa() {
        val d = ErrorLog.describe(IOException("falló", java.net.UnknownHostException("firestore.googleapis.com")))
        assertTrue(d, d.contains("IOException: falló") && d.contains("UnknownHostException"))
    }
}
