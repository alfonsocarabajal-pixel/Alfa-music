package uy.eco.musica

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController

/** Estado del reproductor visible para la interfaz. Refleja lo que hace el servicio. */
class PlayerUi {
    var controller by mutableStateOf<MediaController?>(null)
        private set
    var current by mutableStateOf<MediaItem?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var buffering by mutableStateOf(false)
        private set
    var queue by mutableStateOf<List<MediaItem>>(emptyList())
        private set
    var index by mutableIntStateOf(-1)
        private set

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    fun attach(c: MediaController) {
        controller = c
        c.addListener(listener)
        refresh()
    }

    fun detach() {
        controller?.removeListener(listener)
        controller = null
    }

    private fun refresh() {
        val c = controller ?: return
        current = c.currentMediaItem
        isPlaying = c.isPlaying
        buffering = c.playbackState == Player.STATE_BUFFERING
        index = c.currentMediaItemIndex
        queue = List(c.mediaItemCount) { c.getMediaItemAt(it) }
    }

    /** Toca una canción: suena ya y la radio arma el resto de la cola. */
    fun playNow(song: Song) {
        controller?.run {
            setMediaItem(song.toMediaItem())
            prepare()
            play()
        }
    }

    /** "+": la agrega después de lo que ya agregaste vos, antes de las sugerencias de la radio. */
    fun enqueue(song: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            playNow(song)
            return
        }
        val firstRadio = (c.currentMediaItemIndex + 1 until c.mediaItemCount).firstOrNull {
            c.getMediaItemAt(it).mediaMetadata.extras?.getBoolean(EXTRA_RADIO) == true
        }
        if (firstRadio != null) c.addMediaItem(firstRadio, song.toMediaItem())
        else c.addMediaItem(song.toMediaItem())
    }

    fun toggle() {
        controller?.run {
            if (isPlaying) pause()
            else {
                if (playbackState == Player.STATE_IDLE) prepare()
                play()
            }
        }
    }

    fun next() = controller?.seekToNextMediaItem()
    fun previous() = controller?.seekToPrevious()

    fun playAt(i: Int) {
        controller?.run {
            seekTo(i, 0)
            play()
        }
    }

    fun remove(i: Int) = controller?.removeMediaItem(i)
}
