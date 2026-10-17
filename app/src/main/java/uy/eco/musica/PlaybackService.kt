package uy.eco.musica

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Servicio que reproduce en segundo plano. Media3 se encarga de la notificación,
 * la pantalla bloqueada, los auriculares Bluetooth y Android Auto.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val played = mutableSetOf<String>()
    private var radioSeed: String? = null
    private var retriedId: String? = null

    override fun onCreate() {
        super.onCreate()

        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)

        // "ytm://ID" se traduce a la URL real del audio justo antes de cargarlo (streaming puro).
        val resolving = ResolvingDataSource.Factory(http) { spec -> resolve(spec) }

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(resolving))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true // pausa si entra una llamada u otra app reproduce audio
            )
            .setHandleAudioBecomingNoisy(true) // pausa al desconectar auriculares
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(listener)

        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(callback)
            .build()
    }

    private fun resolve(spec: DataSpec): DataSpec {
        if (spec.uri.scheme != SCHEME) return spec
        val id = spec.uri.toString().removePrefix("$SCHEME://").takeIf { it.length == 11 }
            ?: throw IOException("Canción inválida")
        val resolved = try {
            Repo.resolve(id)
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException(e)
        }
        return spec.buildUpon()
            .setUri(resolved.url)
            .setHttpRequestHeaders(spec.httpRequestHeaders + ("User-Agent" to resolved.userAgent))
            .build()
    }

    // Al pasar por la app, Android puede quitar la URI de cada canción: la reconstruimos.
    private val callback = object : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(
            mediaItems.map { it.buildUpon().setUri("$SCHEME://${it.mediaId}").build() }
                .toMutableList()
        )
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaItem?.let { played += it.mediaId }
            extendRadio()
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) extendRadio()
        }

        override fun onPlayerError(error: PlaybackException) {
            val id = player.currentMediaItem?.mediaId ?: return
            Repo.invalidate(id)
            if (retriedId != id) {
                // Primer fallo: suele ser un enlace vencido. Se pide uno nuevo y se reintenta.
                retriedId = id
                player.prepare()
                player.play()
            } else if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            }
        }
    }

    /** Radio automática: cuando quedan menos de 2 canciones, agrega relacionadas. */
    private fun extendRadio() {
        if (player.mediaItemCount == 0) return
        if (player.currentMediaItemIndex < player.mediaItemCount - 2) return
        val seed = player.currentMediaItem?.mediaId ?: return
        if (seed == radioSeed) return
        radioSeed = seed

        scope.launch {
            val songs = withContext(Dispatchers.IO) { Repo.related(seed) }
            val inQueue = (0 until player.mediaItemCount)
                .map { player.getMediaItemAt(it).mediaId }.toSet()
            val fresh = songs
                .filter { it.id !in inQueue && it.id !in played }
                .take(8)
            if (fresh.isEmpty()) return@launch

            val wasEnded = player.playbackState == Player.STATE_ENDED
            player.addMediaItems(fresh.map { it.toMediaItem(radio = true) })
            if (wasEnded) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Si se cierra la app desde recientes sin estar sonando, se detiene el servicio.
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
