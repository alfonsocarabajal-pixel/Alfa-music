package uy.eco.musica

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController

/** Un elemento de la cola: [index] es su posición real en el reproductor. */
data class QueueEntry(val index: Int, val item: MediaItem)

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
    /** La cola en el orden en que va a sonar (respeta el modo aleatorio). */
    var queue by mutableStateOf<List<QueueEntry>>(emptyList())
        private set
    var index by mutableIntStateOf(-1)
        private set
    var shuffle by mutableStateOf(false)
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
        shuffle = c.shuffleModeEnabled
        queue = playOrder(c).map { QueueEntry(it, c.getMediaItemAt(it)) }
    }

    private fun playOrder(c: Player): List<Int> {
        val t = c.currentTimeline
        if (t.isEmpty) return emptyList()
        val order = ArrayList<Int>(t.windowCount)
        var i = t.getFirstWindowIndex(c.shuffleModeEnabled)
        while (i != C.INDEX_UNSET && order.size < t.windowCount) {
            order += i
            i = t.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, c.shuffleModeEnabled)
        }
        // Por seguridad, si el orden vino incompleto, se agregan los que falten.
        if (order.size < t.windowCount) {
            (0 until t.windowCount).filterNot { it in order }.forEach { order += it }
        }
        return order
    }

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
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
