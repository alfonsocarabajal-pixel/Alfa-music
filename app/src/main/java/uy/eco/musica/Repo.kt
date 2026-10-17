package uy.eco.musica

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val durationSec: Long,
    val thumb: String?,
    val art: String?,
)

const val SCHEME = "ytm"
const val EXTRA_RADIO = "eco_radio"

/** Convierte una canción en un elemento de la cola. El audio se resuelve recién al reproducir. */
fun Song.toMediaItem(radio: Boolean = false): MediaItem =
    MediaItem.Builder()
        .setMediaId(id)
        .setUri("$SCHEME://$id")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setArtworkUri(art?.let(Uri::parse))
                .setExtras(Bundle().apply { putBoolean(EXTRA_RADIO, radio) })
                .build()
        )
        .build()

object Repo {

    private val yt get() = ServiceList.YouTube

    // Los enlaces de audio de YouTube vencen a las ~6 h; los renovamos antes.
    private const val TTL_MS = 3 * 60 * 60 * 1000L

    class Resolved(val info: StreamInfo, val url: String, val userAgent: String, val time: Long)

    private val cache = ConcurrentHashMap<String, Resolved>()

    /** Busca solo canciones (filtro de YouTube Music). */
    fun search(query: String): List<Song> {
        val extractor = yt.getSearchExtractor(
            query, listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS), ""
        )
        extractor.fetchPage()
        return extractor.initialPage.items
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { it.toSong() }
            .distinctBy { it.id }
    }

    /** Obtiene la URL directa del audio de mejor calidad. Bloquea: llamar fuera del hilo principal. */
    fun resolve(id: String): Resolved {
        cache[id]?.let { if (System.currentTimeMillis() - it.time < TTL_MS) return it }

        val info = StreamInfo.getInfo(yt, "https://www.youtube.com/watch?v=$id")
        val audio = info.audioStreams
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl }
            .maxByOrNull { it.averageBitrate }
            ?: throw IOException("No hay audio disponible para esta canción")

        val url = audio.content
        return Resolved(info, url, userAgentFor(url), System.currentTimeMillis())
            .also { cache[id] = it }
    }

    fun invalidate(id: String) {
        cache.remove(id)
    }

    /** Canciones relacionadas para la radio automática. */
    fun related(id: String): List<Song> = runCatching {
        resolve(id).info.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .filter { it.duration in 60..600 } // descarta videos largos y fragmentos
            .mapNotNull { it.toSong() }
    }.getOrDefault(emptyList())

    private fun StreamInfoItem.toSong(): Song? {
        val id = videoId(url) ?: return null
        return Song(
            id = id,
            title = name ?: return null,
            artist = uploaderName.orEmpty(),
            durationSec = duration,
            thumb = pickImage(thumbnails, small = true),
            art = pickImage(thumbnails, small = false),
        )
    }

    private fun pickImage(images: List<Image>, small: Boolean): String? {
        if (images.isEmpty()) return null
        val sorted = images.sortedBy { it.height }
        return if (small) (sorted.firstOrNull { it.height >= 120 } ?: sorted.last()).url
        else sorted.last().url
    }

    private val idRegex = Regex("(?:[?&]v=|youtu\\.be/|/shorts/)([A-Za-z0-9_-]{11})")

    fun videoId(url: String): String? = idRegex.find(url)?.groupValues?.get(1)

    /**
     * YouTube exige que el reproductor se identifique igual que el cliente con el que se
     * obtuvo el enlace (Android, iOS o web). Se consulta al extractor por reflexión para
     * que un cambio interno de la librería no rompa la compilación.
     */
    private fun userAgentFor(url: String): String = runCatching {
        val helper = Class.forName(
            "org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper"
        )
        val loc: Localization = NewPipe.getPreferredLocalization()
        fun check(test: String, getter: String): String? {
            val matches = helper.getMethod(test, String::class.java).invoke(null, url) as Boolean
            return if (matches) {
                helper.getMethod(getter, Localization::class.java).invoke(null, loc) as String
            } else null
        }
        check("isAndroidStreamingUrl", "getAndroidUserAgent")
            ?: check("isIosStreamingUrl", "getIosUserAgent")
    }.getOrNull() ?: Http.USER_AGENT
}
