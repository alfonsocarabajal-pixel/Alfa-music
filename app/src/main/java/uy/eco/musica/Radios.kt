package uy.eco.musica

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.json.JSONArray

/** Una emisora de radio en vivo (AM o FM). */
data class Station(
    val id: String,
    val name: String,
    val band: String,       // "AM" o "FM"
    val frequency: String,  // "99.5", "690" o vacío si no se conoce
    val url: String,
    val logo: String?,
) {
    /** "FM 99.5", "AM 690" o solo "FM". */
    val dial: String get() = listOf(band, frequency).filter { it.isNotBlank() }.joinToString(" ")
}

private const val STATION_PREFIX = "emisora:"
private const val EXTRA_STATION = "eco_emisora"

/** Las emisoras precargadas (app/src/main/assets/emisoras.json). */
object Stations {
    @Volatile private var cache: List<Station>? = null

    fun all(context: Context): List<Station> = cache ?: synchronized(this) {
        cache ?: load(context).also { cache = it }
    }

    private fun load(context: Context): List<Station> = runCatching {
        val text = context.applicationContext.assets.open("emisoras.json").bufferedReader().use { it.readText() }
        val arr = JSONArray(text)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Station(
                id = o.getString("id"),
                name = o.getString("nombre"),
                band = o.optString("banda", "FM"),
                frequency = o.optString("frecuencia"),
                url = o.getString("url"),
                logo = o.optString("logo").ifBlank { null },
            )
        }.sortedWith(compareBy({ it.band }, { it.frequency.toDoubleOrNull() ?: Double.MAX_VALUE }, { it.name }))
    }.getOrDefault(emptyList())

    fun byMediaId(context: Context, mediaId: String): Station? =
        if (!mediaId.startsWith(STATION_PREFIX)) null
        else all(context).firstOrNull { it.id == mediaId.removePrefix(STATION_PREFIX) }
}

/** La emisora como elemento de la cola: se reproduce directo desde su transmisión en vivo. */
fun Station.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(STATION_PREFIX + id)
        .setUri(url)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(name)
                .setArtist("$dial · En vivo")
                .setArtworkUri(logo?.let(Uri::parse))
                .setExtras(Bundle().apply { putBoolean(EXTRA_STATION, true) })
                .build()
        )
        .build()

/** ¿Es una emisora de radio en vivo (y no una canción)? */
val MediaItem.isStation: Boolean
    get() = mediaId.startsWith(STATION_PREFIX)

@Composable
fun RadiosScreen(ui: PlayerUi) {
    val context = LocalContext.current
    val all = Stations.all(context)
    var band by rememberSaveable { mutableStateOf("") } // "" = todas
    val shown = if (band.isBlank()) all else all.filter { it.band == band }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("" to "Todas", "AM" to "AM", "FM" to "FM").forEach { (value, label) ->
                FilterChip(selected = band == value, onClick = { band = value }, label = { Text(label) })
            }
        }
        if (shown.isEmpty()) {
            Text(
                "No hay emisoras cargadas",
                modifier = Modifier.padding(32.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(shown, key = { it.id }) { station ->
                val playing = ui.current?.mediaId == STATION_PREFIX + station.id
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { ui.playStations(shown, shown.indexOf(station)) }
                        .background(
                            if (playing) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StationLogo(station)
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(start = 12.dp)
                    ) {
                        Text(
                            station.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            station.dial,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (playing) {
                        Icon(
                            Icons.Default.GraphicEq,
                            contentDescription = "Sonando",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/** Logo de la emisora; si no tiene (o no carga), se ve la banda: "AM" / "FM". */
@Composable
private fun StationLogo(station: Station) {
    Box(
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            station.band,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        if (station.logo != null) Thumb(station.logo, 48.dp, placeholder = false)
    }
}
