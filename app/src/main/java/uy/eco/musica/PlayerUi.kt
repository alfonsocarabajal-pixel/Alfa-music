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
class PlayerUi(val prefs: Prefs) {
    var controller by mutableStateOf<MediaController?>(null)
        private set

    /** Playlist compartida cargada en la cola (nombre vacío = ninguna). */
    var loadedName by mutableStateOf(prefs.loadedName)
        private set
    var loadedId by mutableStateOf(prefs.loadedId)
        private set

    fun markLoaded(p: Playlist?) {
        loadedId = p?.id.orEmpty()
        loadedName = p?.name.orEmpty()
        prefs.loadedId = loadedId
        prefs.loadedName = loadedName
        prefs.loadedAuthor = p?.author.orEmpty()
    }

    /** Se llama cuando la playlist cargada se guardó, renombró o borró. */
    fun onPlaylistChanged(p: Playlist?, deletedId: String? = null) {
        if (p != null && p.id == loadedId) markLoaded(p)
        if (deletedId != null && deletedId == loadedId) markLoaded(null)
    }

    /** Reproduce una playlist completa desde [start], opcionalmente en orden aleatorio. */
    fun playPlaylist(p: Playlist, start: Int = 0, shuffled: Boolean = false) {
        val c = controller ?: return
        if (p.songs.isEmpty()) return
        val first = if (shuffled) p.songs.indices.random() else start.coerceIn(0, p.songs.size - 1)
        c.shuffleModeEnabled = false
        c.setMediaItems(p.songs.map { it.toMediaItem() }, first, 0)
        if (shuffled) c.shuffleModeEnabled = true
        c.prepare()
        c.play()
        markLoaded(p)
    }

    /**
     * Las canciones a guardar en una playlist: toda la cola en su orden original,
     * menos las sugerencias de la radio que todavía no sonaron.
     */
    fun songsToSave(): List<Song> {
        val c = controller ?: return emptyList()
        // Si se está escuchando una playlist del grupo, las sugerencias de la radio
        // NUNCA se guardan en ella (aunque ya hayan sonado).
        if (loadedId.isNotBlank()) {
            return (0 until c.mediaItemCount)
                .map { c.getMediaItemAt(it) }
                .filterNot { it.isRadio }
                .map { it.toSong() }
        }
        // Cola armada a mano: se guardan las sugerencias que ya sonaron, no las que faltan.
        val upcoming = queue.dropWhile { it.index != c.currentMediaItemIndex }.drop(1)
            .map { it.index }.toSet()
        return (0 until c.mediaItemCount)
            .map { it to c.getMediaItemAt(it) }
            .filterNot { (i, item) -> item.isRadio && i in upcoming }
            .map { (_, item) -> item.toSong() }
    }
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
        markLoaded(null) // la cola nueva ya no es la playlist que estaba cargada
    }

    /** Karaoke: suena solo esa versión (sin radio automática). */
    fun playKaraoke(song: Song, query: String) {
        controller?.run {
            setMediaItem(song.toMediaItem(karaokeQuery = query))
            prepare()
            play()
        }
        markLoaded(null)
    }

    /** Karaoke: la suma al final de la cola. */
    fun enqueueKaraoke(song: Song, query: String) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) playKaraoke(song, query)
        else c.addMediaItem(song.toMediaItem(karaokeQuery = query))
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
