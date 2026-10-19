package uy.eco.musica

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

class MainActivity : ComponentActivity() {

    private val ui by lazy { PlayerUi(Prefs(applicationContext)) }
    private var controllerFuture: ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Android 13+: permiso para mostrar los controles en la notificación.
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        setContent {
            EcoTheme {
                var unlocked by remember { mutableStateOf(access.activated) }
                var notice by remember { mutableStateOf<String?>(null) }

                // Cada vez que se abre: si el acceso fue revocado, se bloquea.
                LaunchedEffect(unlocked) {
                    if (unlocked && Cloud.enabled) {
                        val ok = withContext(Dispatchers.IO) { access.stillValid() }
                        if (!ok) {
                            access.clear()
                            ui.controller?.run { stop(); clearMediaItems() }
                            notice = "Tu acceso fue revocado o el código ya no es válido. Pedí un código nuevo."
                            unlocked = false
                        }
                    }
                }

                if (unlocked) EcoScreen(ui, access)
                else ActivationScreen(access, notice) { notice = null; unlocked = true }
            }
        }
    }

    private val access by lazy { Access(applicationContext) }

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener(
            { runCatching { ui.attach(future.get()) } },
            ContextCompat.getMainExecutor(this)
        )
    }

    override fun onStop() {
        ui.detach()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onStop()
    }
}
